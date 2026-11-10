<#
.SYNOPSIS
  Guest Desk one-click start: dependencies, build and start, seed data, summary.

.DESCRIPTION
  Runs 01-check-dependencies.ps1, 02-build-and-start.ps1 and 03-seed.ps1 (in _Deployment\steps) in order and stops at the
  first error. Safe to run again at any time: an existing .env is kept, the images are rebuilt only
  where something changed, and the seed steps skip what already exists.

.PARAMETER Yes
  Answer "yes" to the installation questions of step 1 (winget installs of Docker Desktop / Python).

.PARAMETER SkipSeed
  Do not run step 3.
#>
param([switch]$Yes, [switch]$SkipSeed)

. (Join-Path $PSScriptRoot 'common.ps1')

$started = Get-Date
Write-Host '############################################################' -ForegroundColor Cyan
Write-Host ' Guest Desk - one-click deployment' -ForegroundColor Cyan
Write-Host '############################################################' -ForegroundColor Cyan

& (Join-Path $PSScriptRoot '01-check-dependencies.ps1') -Yes:$Yes
if ($LASTEXITCODE -ne 0) { Stop-WithError 'Step 1 (dependencies) failed. Nothing was started.' }

& (Join-Path $PSScriptRoot '02-build-and-start.ps1')
if ($LASTEXITCODE -ne 0) { Stop-WithError 'Step 2 (build and start) failed.' }

if (-not $SkipSeed) {
    & (Join-Path $PSScriptRoot '03-seed.ps1')
    if ($LASTEXITCODE -ne 0) { Stop-WithError 'Step 3 (seed data) failed. The stack is running; fix the problem and run steps\03-seed.bat again.' }
}

$total = [int]((Get-Date) - $started).TotalSeconds
Write-Summary
Write-Host "  Total time: $total s"
exit 0
