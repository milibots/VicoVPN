#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TAG="${XRAY_AAR_TAG:-v26.6.27}"
OUT="$ROOT/app/libs/libv2ray.aar"
URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/$TAG/libv2ray.aar"
FALLBACK="https://sourceforge.net/projects/androidlibxraylite.mirror/files/${TAG#v}/libv2ray.aar/download"
mkdir -p "$(dirname "$OUT")"
TMP="$OUT.tmp"
rm -f "$TMP"
echo "Downloading AndroidLibXrayLite $TAG..."
if ! curl --fail --location --retry 4 --connect-timeout 20 --output "$TMP" "$URL"; then
  echo "GitHub failed; trying SourceForge mirror..."
  curl --fail --location --retry 4 --connect-timeout 20 --output "$TMP" "$FALLBACK"
fi
# A real AAR is a ZIP beginning with PK and should be tens of megabytes.
[ "$(wc -c < "$TMP")" -gt 10000000 ] || { echo "Downloaded file is unexpectedly small" >&2; exit 1; }
head -c 2 "$TMP" | grep -q 'PK' || { echo "Downloaded file is not an AAR/ZIP" >&2; exit 1; }
mv "$TMP" "$OUT"
sha256sum "$OUT" | tee "$OUT.sha256"
echo "Installed: $OUT"
