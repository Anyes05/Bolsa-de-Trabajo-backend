#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
out="$here/out"
mkdir -p "$out"

command -v mvn >/dev/null || { echo "Maven no está en PATH"; exit 1; }

echo "SAST backend (PMD reglas de seguridad, Java)..."
(cd "$repo" && mvn -q -DskipTests -Psast pmd:pmd)
if [[ -f "$repo/target/pmd.xml" ]]; then
  cp "$repo/target/pmd.xml" "$out/sast-pmd.xml"
  echo "PMD listo: $out/sast-pmd.xml"
else
  echo "PMD no generó target/pmd.xml (revisá el log de Maven)."
fi
echo "SAST backend listo. Anotá en scripts/seguridad/HALLAZGOS.md"
