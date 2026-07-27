#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"

echo "[1/4] Pure Kotlin parser/config smoke test"
bash scripts/verify-pure-kotlin.sh

echo "[2/4] Android XML well-formedness"
python3 -c 'from pathlib import Path; import xml.etree.ElementTree as ET; files=list(Path(".").rglob("*.xml")); [ET.parse(f) for f in files]; print(f"XML_OK files={len(files)}")'

echo "[3/4] Shell syntax"
bash -n gradlew scripts/bootstrap-native.sh scripts/verify-pure-kotlin.sh scripts/verify-project.sh

echo "[4/4] No known fake-tunnel patterns"
if grep -R -n -E '127\.0\.0\.01|addRoute\("10\.0\.0\.0"|fake.*(ip|location)|random.*(ip|location)' app/src README.md docs --exclude='*.xml'; then
  echo "Unsafe/fake tunnel pattern found" >&2
  exit 1
fi
if grep -R -n 'allowInsecure' app/src; then
  echo "TLS verification bypass found" >&2
  exit 1
fi
echo "PROJECT_SOURCE_VERIFICATION_OK"
