<#
.SYNOPSIS
  Guest Desk, step 1 of 3: checks (and, only after asking, installs) everything the deployment needs.

.DESCRIPTION
  - Docker CLI and Docker Compose v2 ("docker compose"); offers to install Docker Desktop with winget.
  - The Docker engine is running (starts Docker Desktop and waits for it if needed).
  - Python 3.8+ for the seed scripts; offers to install Python 3.12 with winget.
  - A local Python virtual environment in _Deployment\python\.venv with the packages of requirements.txt.
  - _Deployment\docker\.env: created from .env.example with freshly generated random secrets if it does
    not exist. An existing .env is never modified.
  Nothing is installed without asking, unless -Yes is given.

.PARAMETER Yes
  Answer "yes" to every installation question (non-interactive use).
#>
param([switch]$Yes)

. (Join-Path $PSScriptRoot 'common.ps1')

Write-Host 'Guest Desk - step 1/3: dependencies' -ForegroundColor Cyan

# ---------------------------------------------------------------- Docker CLI
Write-Step 'Docker'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Warn 'Docker is not installed (the "docker" command was not found).'
    $winget = Get-Winget
    if (-not $winget) {
        Stop-WithError 'Install Docker Desktop from https://www.docker.com/products/docker-desktop/ and run this script again.'
    }
    Write-Info 'This will run:  winget install -e --id Docker.DockerDesktop'
    Write-Info '(administrator rights and a restart of Windows may be needed; Docker Desktop uses WSL 2).'
    if (-not (Confirm-Action 'Install Docker Desktop now?' $Yes)) {
        Stop-WithError 'Docker Desktop is required. Install it and run this script again.'
    }
    & $winget install -e --id Docker.DockerDesktop --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0) { Stop-WithError "winget could not install Docker Desktop (exit code $LASTEXITCODE)." }
    Update-SessionPath
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Stop-WithError 'Docker Desktop was installed. Restart Windows (or sign out and in), start Docker Desktop once, then run this script again.'
    }
}
Write-Ok ('Docker CLI: ' + ((& docker --version) -join ' '))

if (-not (Test-Command 'docker compose version')) {
    Stop-WithError 'Docker Compose v2 ("docker compose") is not available. Update Docker Desktop.'
}
Write-Ok ('Docker Compose: ' + ((& docker compose version --short) -join ' '))

# ---------------------------------------------------------------- Docker engine
if (-not (Test-Command 'docker info')) {
    $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path $desktop)) {
        Stop-WithError 'The Docker engine is not running. Start Docker Desktop and run this script again.'
    }
    Write-Info 'The Docker engine is not running: starting Docker Desktop (this can take a minute) ...'
    Start-Process -FilePath $desktop | Out-Null
    $deadline = (Get-Date).AddSeconds(180)
    while (-not (Test-Command 'docker info')) {
        if ((Get-Date) -gt $deadline) {
            Stop-WithError 'Docker Desktop did not start within 180 s. Start it by hand, wait until it says "Engine running", then run this script again.'
        }
        Start-Sleep -Seconds 3
    }
}
$osType = ((& docker info --format '{{.OSType}}') -join '').Trim()
if ($osType -ne 'linux') {
    Stop-WithError "Docker is running $osType containers. Switch Docker Desktop to Linux containers and run this script again."
}
Write-Ok 'Docker engine is running (Linux containers).'

# ---------------------------------------------------------------- Python
Write-Step 'Python 3 (for the seed scripts)'
$py = Find-Python
if (-not $py) {
    Write-Warn 'Python 3.8 or newer was not found.'
    $winget = Get-Winget
    if (-not $winget) {
        Stop-WithError 'Install Python 3 from https://www.python.org/downloads/ (tick "Add python.exe to PATH") and run this script again.'
    }
    Write-Info 'This will run:  winget install -e --id Python.Python.3.12   (installs for the current user)'
    if (-not (Confirm-Action 'Install Python 3.12 now?' $Yes)) {
        Stop-WithError 'Python 3 is required for the seed scripts. Install it and run this script again.'
    }
    & $winget install -e --id Python.Python.3.12 --scope user --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0) { Stop-WithError "winget could not install Python (exit code $LASTEXITCODE)." }
    Update-SessionPath
    $py = Find-Python
    if (-not $py) { Stop-WithError 'Python was installed but is not on PATH yet. Open a new terminal and run this script again.' }
}
$pyExe = $py[0]
$pyArgs = @()
if ($py.Count -gt 1) { $pyArgs = $py[1..($py.Count - 1)] }
Write-Ok ('Python: ' + ((& $pyExe @pyArgs --version) -join ' ') + "  (command: $($py -join ' '))")

# ---------------------------------------------------------------- virtual environment + packages
Write-Step 'Python packages (local virtual environment _Deployment\python\.venv)'
if (-not (Test-Path $script:VenvPython)) {
    Write-Info "Creating the virtual environment $script:VenvDir ..."
    & $pyExe @pyArgs -m venv $script:VenvDir
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $script:VenvPython)) {
        Stop-WithError 'Could not create the Python virtual environment (python -m venv).'
    }
}
if (Test-Command "`"$script:VenvPython`" -c `"import requests, bcrypt, psycopg2`"") {
    Write-Ok 'requests, bcrypt and psycopg2-binary are installed.'
} else {
    Write-Info 'Missing packages. Installing into _Deployment\python\.venv only (not system-wide):'
    Write-Info "  pip install -r python/requirements.txt   (requests, bcrypt, psycopg2-binary)"
    & $script:VenvPython -m pip install --disable-pip-version-check -q -r $script:Requirements
    if ($LASTEXITCODE -ne 0) { Stop-WithError 'pip could not install the packages. Check the internet connection and try again.' }
    if (-not (Test-Command "`"$script:VenvPython`" -c `"import requests, bcrypt, psycopg2`"")) {
        Stop-WithError 'The packages were installed but cannot be imported.'
    }
    Write-Ok 'Packages installed.'
}

# ---------------------------------------------------------------- .env
Write-Step 'Configuration (_Deployment\docker\.env)'
if (Test-Path $script:EnvFile) {
    Write-Ok '.env exists and is left untouched.'
} else {
    if (-not (Test-Path $script:EnvExample)) { Stop-WithError '.env.example is missing.' }
    $lines = Get-Content -LiteralPath $script:EnvExample
    $jwt = New-RandomHex 32
    $settings = New-RandomHex 32
    $out = foreach ($line in $lines) {
        if ($line -match '^JWT_SECRET=') { "JWT_SECRET=$jwt" }
        elseif ($line -match '^SETTINGS_SECRET=') { "SETTINGS_SECRET=$settings" }
        else { $line }
    }
    [System.IO.File]::WriteAllText($script:EnvFile, (($out -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($false)))
    Write-Ok '.env created from .env.example with newly generated random JWT_SECRET and SETTINGS_SECRET.'
    Write-Info 'For a kiosk tablet, set PUBLIC_API_URL in .env to the LAN address of this PC (see the Start Guide).'
}
$envValues = Read-EnvFile
foreach ($key in 'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'JWT_SECRET', 'SETTINGS_SECRET') {
    if (-not $envValues.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($envValues[$key])) {
        Stop-WithError "$key is empty or missing in _Deployment\docker\.env. Set it (or delete .env so a new one is generated)."
    }
}
Write-Ok 'Required variables are set.'

Write-Host ''
Write-Host 'Step 1/3 done: all dependencies are in place.' -ForegroundColor Green
exit 0
