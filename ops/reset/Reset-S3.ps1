[CmdletBinding()]
param(
 [Parameter(Mandatory)][string]$Manifest,
 [Parameter(Mandatory)][string]$Bucket,
 [Parameter(Mandatory)][ValidatePattern('^\d{12}$')][string]$AccountId,
 [Parameter(Mandatory)][string]$Region,
 [Parameter(Mandatory)][string]$DeployedBucket,
 [string]$ObjectBackupEvidence,
 [switch]$Apply,
 [string]$ConfirmBucket,
 [switch]$ApplicationStopped,
 [string]$ExpectedManifestSha256,
 [switch]$AllowIrreversibleWithoutBackup
)
$ErrorActionPreference = 'Stop'
function Invoke-ResetAws {
 param([Parameter(ValueFromRemainingArguments)][string[]]$Arguments)
 $raw = & aws @Arguments --region $Region --output json --no-cli-pager 2>$null
 if ($LASTEXITCODE) { throw 'AWS operation failed; check permissions, retention/MFA, region and account securely. No automatic retry.' }
 if ($raw) { return ($raw -join "`n" | ConvertFrom-Json) }
}
if ($Bucket -cne $DeployedBucket) { throw 'Bucket does not match independently verified deployment configuration' }
if ($AllowIrreversibleWithoutBackup -and $ObjectBackupEvidence) { throw 'Choose backup evidence or explicit irreversible mode, not both' }
if (!$AllowIrreversibleWithoutBackup -and (!$ObjectBackupEvidence -or !(Test-Path $ObjectBackupEvidence -PathType Leaf) -or (Get-Item $ObjectBackupEvidence).Length -eq 0)) {
 throw 'Object backup and restore evidence file required (also for dry-run)'
}
if ($Apply -and (!$ApplicationStopped -or $ConfirmBucket -cne $Bucket -or !$ExpectedManifestSha256)) {
 throw 'Apply requires stopped application, exact bucket confirmation and reviewed manifest SHA256'
}
if ($ExpectedManifestSha256 -and (Get-FileHash $Manifest -Algorithm SHA256).Hash -cne $ExpectedManifestSha256) {
 throw 'Manifest hash mismatch'
}
$m = Get-Content -Raw $Manifest | ConvertFrom-Json
if ($m.format -ne 1 -or (!$m.backupSha256 -and !($AllowIrreversibleWithoutBackup -and $m.irreversibleWithoutBackup -eq $true)) -or ($Apply -and $m.committed -ne $true)) {
 throw 'Require valid database receipt; S3 apply requires committed database reset'
}
$keys = @($m.keys | Sort-Object -Unique)
foreach ($key in $keys) {
 if ($key -cnotmatch '^postulantes/[0-9]+/cvs/[^/]+$') { throw 'Unexpected key; manual review required' }
}
$identity = Invoke-ResetAws @('sts','get-caller-identity')
if ($identity.Account -cne $AccountId) { throw 'AWS account mismatch' }
$null = Invoke-ResetAws @('s3api','head-bucket','--bucket',$Bucket,'--expected-bucket-owner',$AccountId)
$location = Invoke-ResetAws @('s3api','get-bucket-location','--bucket',$Bucket,'--expected-bucket-owner',$AccountId)
$actualRegion = if (!$location.LocationConstraint) { 'us-east-1' } elseif ($location.LocationConstraint -eq 'EU') { 'eu-west-1' } else { $location.LocationConstraint }
if ($actualRegion -cne $Region) { throw 'Bucket region mismatch' }
$versioning = Invoke-ResetAws @('s3api','get-bucket-versioning','--bucket',$Bucket,'--expected-bucket-owner',$AccountId)
if ($versioning.MFADelete -eq 'Enabled') { throw 'MFA Delete enabled; use separately reviewed MFA procedure' }
$plan = [System.Collections.Generic.List[object]]::new()
foreach ($key in $keys) {
 if ($versioning.Status -in @('Enabled','Suspended')) {
  # AWS CLI auto-pagination remains enabled; prefix results are filtered by exact key.
  $versions = Invoke-ResetAws @('s3api','list-object-versions','--bucket',$Bucket,'--prefix',$key,'--expected-bucket-owner',$AccountId)
  foreach ($v in @($versions.Versions) + @($versions.DeleteMarkers)) {
   if ($null -ne $v -and $v.Key -ceq $key) {
    if (!$v.VersionId) { throw 'Missing version ID' }
    $plan.Add(@{ Key=$key; VersionId=$v.VersionId })
   }
  }
 } else {
  # Listing avoids treating AccessDenied as an absent object. Never delete prefix siblings.
  $objects = Invoke-ResetAws @('s3api','list-objects-v2','--bucket',$Bucket,'--prefix',$key,'--expected-bucket-owner',$AccountId)
  foreach ($o in @($objects.Contents)) {
   if ($null -ne $o -and $o.Key -ceq $key) { $plan.Add(@{Key=$key; ETag=$o.ETag}) }
  }
 }
}
if (!$Apply) { Write-Output "Dry-run: $($keys.Count) exact keys, $($plan.Count) objects/versions. No writes."; return }
# Database has already committed. Each S3 operation can independently fail.
# Re-running the same reviewed receipt resumes remaining exact keys/versions.
foreach ($item in $plan) {
 $arguments = @('s3api','delete-object','--bucket',$Bucket,'--key',$item.Key,'--expected-bucket-owner',$AccountId)
 if ($item.VersionId) { $arguments += @('--version-id',$item.VersionId) }
 else { $arguments += @('--if-match',$item.ETag) }
 $null = Invoke-ResetAws $arguments
}
Write-Output "S3 phase completed: $($plan.Count) explicit object/version requests. Verify with a new dry-run."
