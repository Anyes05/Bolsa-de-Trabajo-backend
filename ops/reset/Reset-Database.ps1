[CmdletBinding()]
param(
 [Parameter(Mandatory)][string]$Database,
 [string]$Backup,
 [Parameter(Mandatory)][string]$Manifest,
 [switch]$Apply,
 [string]$ConfirmDatabase,
 [switch]$ApplicationStopped,
 [switch]$BackupRestoreVerified,
 [switch]$AllowIrreversibleWithoutBackup
)
$ErrorActionPreference = 'Stop'
if (Test-Path $Manifest) { throw 'Manifest must not already exist' }
if ($AllowIrreversibleWithoutBackup -and ($Backup -or $BackupRestoreVerified)) { throw 'Choose backup or explicit irreversible mode, not both' }
if (!$AllowIrreversibleWithoutBackup -and (!$Backup -or !(Test-Path $Backup -PathType Leaf) -or (Get-Item $Backup).Length -eq 0)) { throw 'Nonempty pg_dump backup required' }
if ($Apply -and (!$ApplicationStopped -or (!$BackupRestoreVerified -and !$AllowIrreversibleWithoutBackup) -or $ConfirmDatabase -cne $Database)) {
 throw 'Apply requires stopped application, exact database confirmation and tested restore or explicit irreversible opt-in'
}
# libpq PGHOST/PGPORT/PGUSER/PGPASSFILE or PGSERVICE supply credentials, never command-line passwords.
$hash = $null
if (!$AllowIrreversibleWithoutBackup) {
$toc = & pg_restore --list $Backup 2>$null
if ($LASTEXITCODE) { throw 'Backup is not a readable pg_dump custom archive' }
if (!($toc | Where-Object { $_ -match ('^;\s+dbname:\s+' + [regex]::Escape($Database) + '\s*$') })) {
 throw 'Backup database name does not match target'
}
$hash = (Get-FileHash $Backup -Algorithm SHA256).Hash
}
$result = & psql -X -qAt --dbname $Database --set ON_ERROR_STOP=1 --set "database=$Database" --set "apply=$($Apply.IsPresent.ToString().ToLowerInvariant())" --file "$PSScriptRoot\reset.sql" 2>$null
if ($LASTEXITCODE) { throw 'Reset failed/rolled back; inspect database server diagnostics securely' }
$receipt = ($result -join "`n") | ConvertFrom-Json
$receipt | Add-Member backupSha256 $hash
$receipt | Add-Member irreversibleWithoutBackup $AllowIrreversibleWithoutBackup.IsPresent
$receipt | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 $Manifest
Write-Output "Reset completed. Applied: $($Apply.IsPresent). Manifest stored; protect as sensitive."
