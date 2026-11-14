$ErrorActionPreference = 'Stop'
$directory = Join-Path $PSScriptRoot 'test-output'
$null = New-Item -ItemType Directory -Path $directory -Force
$global:resetFakeCalls = [System.Collections.Generic.List[object]]::new()
$global:resetFakeMode = 'unversioned'
function aws {
 $a = @($args)
 $global:LASTEXITCODE = 0
 $global:resetFakeCalls.Add($a)
 $operation = $a[1]
 if ($global:resetFakeMode -eq 'denied') { $global:LASTEXITCODE = 1; return }
 switch ($operation) {
  'get-caller-identity' { '{"Account":"123456789012"}' }
  'head-bucket' { '{}' }
  'get-bucket-location' { '{"LocationConstraint":null}' }
  'get-bucket-versioning' {
   if ($global:resetFakeMode -eq 'versioned') { '{"Status":"Enabled"}' }
   elseif ($global:resetFakeMode -eq 'suspended') { '{"Status":"Suspended"}' }
   elseif ($global:resetFakeMode -eq 'mfa') { '{"Status":"Enabled","MFADelete":"Enabled"}' }
   else { '{}' }
  }
  'list-objects-v2' { '{"Contents":[{"Key":"postulantes/42/cvs/test.pdf","ETag":"test-etag"},{"Key":"postulantes/42/cvs/test.pdf.other","ETag":"other"}]}' }
  'list-object-versions' {
   '{"Versions":[{"Key":"postulantes/42/cvs/test.pdf","VersionId":"v1"},{"Key":"postulantes/42/cvs/test.pdf","VersionId":"null"},{"Key":"postulantes/42/cvs/test.pdf.other","VersionId":"other"}],"DeleteMarkers":[{"Key":"postulantes/42/cvs/test.pdf","VersionId":"marker"}]}'
  }
  'delete-object' { '{}' }
  default { throw "Unexpected AWS operation: $operation" }
 }
}
function Assert-True($condition, $message) { if (!$condition) { throw $message } }
try {
 $manifest = Join-Path $directory 'manifest.json'
 $evidence = Join-Path $directory 'evidence.txt'
 '{"format":1,"committed":true,"backupSha256":"synthetic","keys":["postulantes/42/cvs/test.pdf"]}' | Set-Content $manifest
 'Synthetic backup evidence, tests only' | Set-Content $evidence
 $parameters = @{
  Manifest=$manifest; Bucket='explicit-test-bucket'; DeployedBucket='explicit-test-bucket'
  AccountId='123456789012'; Region='us-east-1'; ObjectBackupEvidence=$evidence
 }
 & "$PSScriptRoot\Reset-S3.ps1" @parameters
 Assert-True (@($global:resetFakeCalls | Where-Object { $_[1] -eq 'delete-object' }).Count -eq 0) 'Dry-run wrote to S3'
 foreach ($mode in @('unversioned','versioned','suspended')) {
  $global:resetFakeMode = $mode
  $global:resetFakeCalls.Clear()
  & "$PSScriptRoot\Reset-S3.ps1" @parameters -Apply -ApplicationStopped -ConfirmBucket 'explicit-test-bucket' -ExpectedManifestSha256 (Get-FileHash $manifest).Hash
  $deletes = @($global:resetFakeCalls | Where-Object { $_[1] -eq 'delete-object' })
  $expected = if ($mode -eq 'unversioned') { 1 } else { 3 }
  Assert-True ($deletes.Count -eq $expected) 'Incorrect deletion count'
  foreach ($call in $deletes) {
   Assert-True ($call[([array]::IndexOf($call,'--key') + 1)] -ceq 'postulantes/42/cvs/test.pdf') 'Deleted unrelated key'
   Assert-True ($call -contains '--expected-bucket-owner') 'Missing owner guard'
   Assert-True ($call -contains $(if ($mode -eq 'unversioned') { '--if-match' } else { '--version-id' })) 'Missing concurrency/version guard'
  }
 }
 foreach ($mode in @('denied','mfa')) {
  $global:resetFakeMode = $mode
  $failed = $false
  try { & "$PSScriptRoot\Reset-S3.ps1" @parameters } catch { $failed = $true }
  Assert-True $failed 'Permission/MFA error was ignored'
 }
 $global:resetFakeMode = 'unversioned'
 $parameters.Remove('ObjectBackupEvidence')
 '{"format":1,"committed":true,"backupSha256":null,"irreversibleWithoutBackup":true,"keys":["postulantes/42/cvs/test.pdf"]}' | Set-Content $manifest
 $failed = $false
 try { & "$PSScriptRoot\Reset-S3.ps1" @parameters } catch { $failed = $true }
 Assert-True $failed 'No-backup receipt accepted without explicit opt-in'
 $global:resetFakeCalls.Clear()
 & "$PSScriptRoot\Reset-S3.ps1" @parameters -AllowIrreversibleWithoutBackup -Apply -ApplicationStopped -ConfirmBucket 'explicit-test-bucket' -ExpectedManifestSha256 (Get-FileHash $manifest).Hash
 Assert-True (@($global:resetFakeCalls | Where-Object { $_[1] -eq 'delete-object' }).Count -eq 1) 'Explicit no-backup opt-in did not delete exact key'
 foreach ($failure in @('hash','confirmation','stopped','receipt')) {
  $hash = (Get-FileHash $manifest).Hash
  $confirm = 'explicit-test-bucket'
  $stopped = $true
  if ($failure -eq 'hash') { $hash = 'wrong' }
  if ($failure -eq 'confirmation') { $confirm = 'wrong' }
  if ($failure -eq 'stopped') { $stopped = $false }
  if ($failure -eq 'receipt') { '{"format":1,"committed":true,"keys":[]}' | Set-Content $manifest; $hash = (Get-FileHash $manifest).Hash }
  $failed = $false
  try { & "$PSScriptRoot\Reset-S3.ps1" @parameters -AllowIrreversibleWithoutBackup -Apply -ApplicationStopped:$stopped -ConfirmBucket $confirm -ExpectedManifestSha256 $hash } catch { $failed = $true }
  Assert-True $failed "Irreversible mode bypassed guard: $failure"
 }
 $parameters.DeployedBucket = 'wrong'
 $failed = $false
 try { & "$PSScriptRoot\Reset-S3.ps1" @parameters } catch { $failed = $true }
 Assert-True $failed 'Deployment mismatch ignored'
 Write-Output 'S3 double tests passed; no AWS contacted.'
} finally {
 Remove-Item function:\aws
 Remove-Item $directory -Recurse -Force
 Remove-Variable resetFakeCalls,resetFakeMode -Scope Global
}
