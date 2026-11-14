$ErrorActionPreference = 'Stop'
$directory = Join-Path $PSScriptRoot 'guard-test-output'
$null = New-Item -ItemType Directory $directory -Force
$global:guardCalls = 0
function psql {
 $global:guardCalls++
 $global:LASTEXITCODE = 0
 '{"format":1,"database":"test","committed":false,"keys":[]}'
}
function Assert-Rejected([scriptblock]$Action) {
 $failed = $false
 try { & $Action } catch { $failed = $true }
 if (!$failed) { throw 'Unsafe invocation accepted' }
}
try {
 $manifest = Join-Path $directory 'receipt.json'
 $db = Join-Path $PSScriptRoot 'Reset-Database.ps1'
 Assert-Rejected { & $db -Database test -Manifest $manifest }
 Assert-Rejected { & $db -Database test -Manifest $manifest -AllowIrreversibleWithoutBackup -Apply -ConfirmDatabase test }
 Assert-Rejected { & $db -Database test -Manifest $manifest -AllowIrreversibleWithoutBackup -Apply -ApplicationStopped -ConfirmDatabase wrong }
 Assert-Rejected { & $db -Database test -Manifest $manifest -AllowIrreversibleWithoutBackup -Backup nonexistent }
 if ($global:guardCalls -ne 0) { throw 'Guard contacted database' }
 & $db -Database test -Manifest $manifest -AllowIrreversibleWithoutBackup
 $receipt = Get-Content -Raw $manifest | ConvertFrom-Json
 if (!$receipt.irreversibleWithoutBackup -or $null -ne $receipt.backupSha256) { throw 'Incorrect no-backup receipt' }
 $s3 = Join-Path $PSScriptRoot 'Reset-S3.ps1'
 Assert-Rejected { & $s3 -Manifest $manifest -Bucket test -DeployedBucket test -AccountId 123456789012 -Region us-east-1 }
 Assert-Rejected { & $s3 -Manifest $manifest -Bucket test -DeployedBucket test -AccountId 123456789012 -Region us-east-1 -AllowIrreversibleWithoutBackup -Apply -ApplicationStopped -ConfirmBucket test -ExpectedManifestSha256 (Get-FileHash $manifest).Hash }
 Write-Output 'Irreversible opt-in guards passed; no database or AWS contacted.'
} finally {
 Remove-Item function:\psql
 Remove-Item $directory -Recurse -Force
 Remove-Variable guardCalls -Scope Global
}
