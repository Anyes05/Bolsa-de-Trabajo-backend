$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$repo = Split-Path -Parent (Split-Path -Parent $here)
$out = Join-Path $here "out"
New-Item -ItemType Directory -Force -Path $out | Out-Null

if (Get-Command trivy -ErrorAction SilentlyContinue) {
  Write-Host "SCA Trivy (backend)..."
  trivy fs --scanners vuln,secret --format table $repo
  trivy fs --format json -o (Join-Path $out "sca-trivy.json") $repo
  Write-Host "Trivy listo: $out\sca-trivy.json"
} else {
  Write-Host "Trivy no está en PATH; SCA con lista Maven + OSV.dev."
}

$jdk17 = "C:\Program Files\Java\jdk-17"
if (Test-Path $jdk17) {
  $env:JAVA_HOME = $jdk17
  $env:Path = "$jdk17\bin;" + $env:Path
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
  Write-Host "Maven no está en PATH; no se puede listar dependencias."
  exit 1
}

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
  Write-Host "Hace falta Node.js solo para consultar OSV.dev (no hay Python ni cuenta de OSS Index)."
  exit 1
}

$depsFile = Join-Path $out "maven-deps.txt"
Write-Host "SCA backend (lista Maven + OSV.dev)..."
Push-Location $repo
try {
  mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.8.1:list "-DoutputFile=$depsFile" "-DappendOutput=false"
} finally {
  Pop-Location
}
node (Join-Path $here "osv-maven.mjs")

Write-Host "SCA backend listo. Anotá en scripts/seguridad/HALLAZGOS.md"
