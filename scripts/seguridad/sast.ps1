$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$repo = Split-Path -Parent (Split-Path -Parent $here)
$out = Join-Path $here "out"
New-Item -ItemType Directory -Force -Path $out | Out-Null

$jdk17 = "C:\Program Files\Java\jdk-17"
if (Test-Path $jdk17) {
  $env:JAVA_HOME = $jdk17
  $env:Path = "$jdk17\bin;" + $env:Path
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
  Write-Host "Maven no está en PATH; no se puede correr PMD."
  exit 1
}

Write-Host "SAST backend (PMD reglas de seguridad, Java)..."
$pmdXml = Join-Path $out "sast-pmd.xml"
Push-Location $repo
try {
  mvn -q -DskipTests -Psast pmd:pmd
  $generated = Join-Path $repo "target\pmd.xml"
  if (Test-Path $generated) {
    Copy-Item $generated $pmdXml -Force
    Write-Host "PMD listo: $pmdXml"
  } else {
    Write-Host "PMD no generó target\pmd.xml (revisá el log de Maven)."
  }
} finally {
  Pop-Location
}

Write-Host "SAST backend listo. Anotá en scripts/seguridad/HALLAZGOS.md"
