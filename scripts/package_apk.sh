#!/usr/bin/env bash
set -euo pipefail

variant="${1:?Usage: scripts/package_apk.sh debug|release}"
case "$variant" in debug|release) ;; *) exit 2 ;; esac
version="$(python3 scripts/release.py metadata | python3 -c 'import json,sys; print(json.load(sys.stdin)["version"])')"
filename="Vic2Ray-VPN-${version}-${variant}.apk"
mkdir -p "dist/$variant"
cp "app/build/outputs/apk/$variant/$filename" "dist/$variant/$filename"
(
  cd "dist/$variant"
  sha256sum "$filename" > SHA256SUMS
  sha256sum --check SHA256SUMS
)
