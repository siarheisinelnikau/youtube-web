#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_KEYSTORE_BASE64:?Missing Android keystore secret}"
: "${ANDROID_KEYSTORE_PASSWORD:?Missing Android keystore password secret}"
manifest=.github/release.json
tag=$(jq -er '.tag | select(test("^v[0-9]+\\.[0-9]+\\.[0-9]+$"))' "$manifest")
version=${tag#v}
version_code=$(jq -er '.version_code | select(type == "number" and . > 0 and floor == .)' "$manifest")
alias=${GH_REPO##*/}
expected=$(cat .github/android-signing.sha256)
[[ "$expected" =~ ^[a-f0-9]{64}$ ]]
tools="$ANDROID_HOME/build-tools/35.0.0"
out="$RUNNER_TEMP/signed-apks"
mkdir -p "$out"

umask 077
keystore=$(mktemp "$RUNNER_TEMP/android-keystore.XXXXXXXX")
trap 'rm -f -- "$keystore"' EXIT
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$keystore"
unset ANDROID_KEYSTORE_BASE64

while IFS= read -r name; do
  case "$GH_REPO:$name" in
    "siarheisinelnikau/voyah-radio:VoyahRadio-$tag.apk") package=app.voyahradio ;;
    "siarheisinelnikau/voyah-music:VoyahMusic-$tag.apk") package=app.voyahmusic.player ;;
    "siarheisinelnikau/voyah-music:VoyahMusicTransfer-$tag.apk") package=app.voyahmusic.transfer ;;
    "siarheisinelnikau/youtube-web:YouTubeWeb-$tag.apk") package=org.openvoyah.youtube ;;
    *) echo "::error::Unexpected Android release asset: $name"; exit 1 ;;
  esac
  apk="$out/$name"
  test ! -e "$apk"
  "$tools/apksigner" sign --ks "$keystore" --ks-type PKCS12 --ks-key-alias "$alias"     --ks-pass env:ANDROID_KEYSTORE_PASSWORD --v4-signing-enabled false     --out "$apk" "$RUNNER_TEMP/unsigned-apks/$name"
  "$tools/zipalign" -c 4 "$apk"
  cert=$("$tools/apksigner" verify --min-sdk-version 28 --print-certs "$apk" | awk -F ': ' '/certificate SHA-256 digest:/ { print $2 }')
  test "$cert" = "$expected"
  badging=$("$tools/aapt" dump badging "$apk")
  grep -Fq "package: name='$package' versionCode='$version_code' versionName='$version'" <<< "$badging"
  grep -Fxq "sdkVersion:'28'" <<< "$badging"
  if grep -Fq application-debuggable <<< "$badging"; then
    echo "::error::The release APK is debuggable: $name"
    exit 1
  fi
done < <(jq -er '.assets[].name' "$manifest")
