<#
.SYNOPSIS
  Guest Desk, step 3 of 3: creates the first platform administrator and the demo data.

.DESCRIPTION
  Runs seed_admin.py and then seed_data.py with the Python of _Deployment\python\.venv (created by
  01-check-dependencies). Both scripts wait until the backend and its tables are ready and are
  idempotent: what already exists is skipped, so this step is safe to run again.
  The database name, user and password are taken from _Deployment\docker\.env.

.PARAMETER AdminOnly
  Create only the platform administrator, no demo data.
#>
param([switch]$AdminOnly)

. (Join-Path $PSScriptRoot 'common.ps1')

Write-Host 'Guest Desk - step 3/3: seed data' -ForegroundColor Cyan

if (-not (Test-Path $script:VenvPython)) {
    Stop-WithError 'The Python environment _Deployment\python\.venv does not exist. Run steps\01-check-dependencies.bat (or start-all.bat) first.'
}
$envValues = Read-EnvFile
if ($envValues.Count -eq 0) { Stop-WithError '_Deployment\docker\.env is missing. Run steps\01-check-dependencies.bat (or start-all.bat) first.' }

# The seed scripts read these; they must match the database of the compose stack.
$env:DB_HOST = 'localhost'
$env:DB_PORT = '5432'
$env:DB_NAME = $envValues['POSTGRES_DB']
$env:DB_USER = $envValues['POSTGRES_USER']
$env:DB_PASSWORD = $envValues['POSTGRES_PASSWORD']
$env:BASE_URL = 'http://localhost:8080'
$env:PYTHONIOENCODING = 'utf-8:replace'

Write-Step 'Platform administrator (seed_admin.py)'
& $script:VenvPython (Join-Path $script:PythonDir 'seed_admin.py')
if ($LASTEXITCODE -ne 0) { Stop-WithError 'seed_admin.py failed (see the message above).' }

if (-not $AdminOnly) {
    Write-Step 'Demo owners, properties and products (seed_data.py)'
    & $script:VenvPython (Join-Path $script:PythonDir 'seed_data.py')
    if ($LASTEXITCODE -ne 0) { Stop-WithError 'seed_data.py failed (see the message above).' }
}

Write-Host ''
Write-Host 'Step 3/3 done: seed data is in place.' -ForegroundColor Green
exit 0
