#!/usr/bin/env bash
set -euo pipefail

manifest=.github/release.json
tag=$(jq -er '.tag | select(test("^v[0-9]+\\.[0-9]+\\.[0-9]+$"))' "$manifest")
commit=$(jq -er '.commit | select(test("^[a-f0-9]{40}$"))' "$manifest")
title=$(jq -er '.title | select(length > 0)' "$manifest")
git merge-base --is-ancestor "$commit" HEAD
if git show-ref --verify --quiet "refs/tags/$tag"; then
  test "$(git rev-list -n 1 "refs/tags/$tag")" = "$commit"
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
jq -er '.notes | select(length > 0)' "$manifest" > "$work/release-notes.md"
jq -e '.assets | type == "array" and length > 0' "$manifest" > /dev/null
assets=()
while IFS= read -r asset; do
  name=$(jq -er '.name | select(test("^[A-Za-z0-9]+-v[0-9]+\\.[0-9]+\\.[0-9]+\\.apk$"))' <<< "$asset")
  [[ "$name" == *-"$tag".apk ]]
  apk="$work/$name"
  test ! -e "$apk"
  if [[ -n "${SIGNED_APK_DIR:-}" ]]; then
    install -m 0644 "$SIGNED_APK_DIR/$name" "$apk"
    expected=$(sha256sum "$apk" | cut -d ' ' -f 1)
  else
    expected=$(jq -er '.sha256 | select(test("^[a-f0-9]{64}$"))' <<< "$asset")
    jq -e '.blobs | type == "array" and length > 0 and all(.[]; test("^[a-f0-9]{40}$"))' <<< "$asset" > /dev/null
    while IFS= read -r blob; do
      gh api "repos/$GH_REPO/git/blobs/$blob" --jq .content | base64 --decode
    done < <(jq -r '.blobs[]' <<< "$asset") > "$apk"
  fi
  printf '%s  %s\n' "$expected" "$name" > "$apk.sha256"
  (cd "$work" && sha256sum --check "$name.sha256")
  unzip -tq "$apk"
  assets+=("$apk" "$apk.sha256")
done < <(jq -c '.assets[]' "$manifest")

matching_release() {
  gh api --paginate "repos/$GH_REPO/releases?per_page=100" |
    jq -c --arg tag "$tag" '.[] | select(.tag_name == $tag)'
}

release=$(matching_release)
if [[ -z "$release" ]]; then
  gh release create "$tag" --target "$commit" --title "$title" --notes-file "$work/release-notes.md" --draft
  release=$(matching_release)
fi
id=$(jq -er --arg commit "$commit" 'select(.draft and .target_commitish == $commit) | .id' <<< "$release")

asset_list() {
  gh api "repos/$GH_REPO/releases/$id/assets?per_page=100"
}

for asset in "${assets[@]}"; do
  name=${asset##*/}
  expected="sha256:$(sha256sum "$asset" | cut -d ' ' -f 1)"
  remote=$(asset_list | jq -r --arg name "$name" '.[] | select(.name == $name) | .digest')
  if [[ -z "$remote" ]]; then
    gh release upload "$tag" "$asset"
    remote=$(asset_list | jq -r --arg name "$name" '.[] | select(.name == $name) | .digest')
  fi
  if [[ "$remote" != "$expected" ]]; then
    echo "::error::Remote asset has an unexpected checksum: $name"
    exit 1
  fi
done

asset_list | jq -e --argjson count "${#assets[@]}" 'length == $count' > /dev/null
gh release edit "$tag" --draft=false --latest
