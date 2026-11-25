#!/usr/bin/env bash
set -euo pipefail

manifest=.github/release.json
tag=$(jq -er '.tag | select(test("^v[0-9]+\\.[0-9]+\\.[0-9]+$"))' "$manifest")
commit=$(jq -er '.commit | select(test("^[a-f0-9]{40}$"))' "$manifest")
git merge-base --is-ancestor "$commit" HEAD

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
git archive "$commit" | tar -x -C "$work"
out="$RUNNER_TEMP/unsigned-apks"
mkdir -p "$out"

case "$GH_REPO" in
  siarheisinelnikau/voyah-radio)
    bash "$work/gradlew" -p "$work" :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
    cp "$work/app/build/outputs/apk/release/app-release-unsigned.apk" "$out/VoyahRadio-$tag.apk"
    ;;
  siarheisinelnikau/voyah-music)
    bash "$work/gradlew" -p "$work" :shared:testReleaseUnitTest :player:testReleaseUnitTest       :player:lintRelease :transfer:lintRelease :player:assembleRelease :transfer:assembleRelease
    cp "$work/player/build/outputs/apk/release/player-release-unsigned.apk" "$out/VoyahMusic-$tag.apk"
    cp "$work/transfer/build/outputs/apk/release/transfer-release-unsigned.apk" "$out/VoyahMusicTransfer-$tag.apk"
    ;;
  siarheisinelnikau/youtube-web)
    bash "$work/gradlew" -p "$work" testReleaseUnitTest lintRelease assembleRelease
    cp "$work/build/outputs/apk/release/youtube-web-release-unsigned.apk" "$out/YouTubeWeb-$tag.apk"
    ;;
  *) echo "::error::Unknown Android release repository"; exit 1 ;;
esac
