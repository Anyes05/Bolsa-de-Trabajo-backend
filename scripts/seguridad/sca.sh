#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
out="$here/out"
mkdir -p "$out"

if command -v trivy >/dev/null; then
  echo "SCA Trivy (backend)..."
  trivy fs --scanners vuln,secret --format table "$repo"
  trivy fs --format json -o "$out/sca-trivy.json" "$repo"
else
  echo "Trivy no está en PATH; SCA con lista Maven + OSV.dev."
fi

command -v mvn >/dev/null || { echo "Maven no está en PATH"; exit 1; }
command -v node >/dev/null || { echo "Hace falta Node.js para consultar OSV.dev"; exit 1; }

echo "SCA backend (lista Maven + OSV.dev)..."
(cd "$repo" && mvn -q \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:list \
  -DoutputFile="$out/maven-deps.txt" -DappendOutput=false)
node "$here/osv-maven.mjs"
echo "SCA backend listo. Anotá en scripts/seguridad/HALLAZGOS.md"
