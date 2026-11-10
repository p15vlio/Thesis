# Shared helpers for the Guest Desk deployment scripts (Windows PowerShell 5.1 and PowerShell 7).
# Dot-sourced by the scripts in _Deployment\steps. Console output is ASCII only on purpose
# (Greek Windows consoles use code page 1253).

$script:DeployDir = Split-Path -Parent $PSScriptRoot
$script:DockerDir = Join-Path $script:DeployDir 'docker'
$script:PythonDir = Join-Path $script:DeployDir 'python'
$script:EnvFile = Join-Path $script:DockerDir '.env'
$script:EnvExample = Join-Path $script:DockerDir '.env.example'
$script:VenvDir = Join-Path $script:PythonDir '.venv'
$script:VenvPython = Join-Path $script:VenvDir 'Scripts\python.exe'
$script:Requirements = Join-Path $script:PythonDir 'requirements.txt'

function Write-Step([string]$Text) { Write-Host ''; Write-Host "==> $Text" -ForegroundColor Cyan }
function Write-Ok([string]$Text) { Write-Host "    [OK] $Text" -ForegroundColor Green }
function Write-Info([string]$Text) { Write-Host "    $Text" }
function Write-Warn([string]$Text) { Write-Host "    [WARN] $Text" -ForegroundColor Yellow }

function Stop-WithError([string]$Text) {
    Write-Host ''
    Write-Host "[ERROR] $Text" -ForegroundColor Red
    exit 1
}

# Runs a command line through cmd.exe with all output discarded and returns the exit code.
# (Redirecting a native command's stderr inside Windows PowerShell 5.1 turns it into error records.)
function Test-Command([string]$CommandLine) {
    cmd /c "$CommandLine >nul 2>&1"
    return ($LASTEXITCODE -eq 0)
}

function Confirm-Action([string]$Question, [bool]$AssumeYes) {
    if ($AssumeYes) { Write-Info "$Question -> yes (-Yes)"; return $true }
    try {
        $answer = Read-Host "    $Question [y/N]"
    } catch {
        return $false
    }
    return ($answer -match '^(y|yes)$')
}

# Reloads PATH from the registry so tools installed a moment ago (winget) are found in this session.
function Update-SessionPath {
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $user = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = "$machine;$user"
}

function Get-Winget {
    $cmd = Get-Command winget -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidate = Join-Path $env:LOCALAPPDATA 'Microsoft\WindowsApps\winget.exe'
    if (Test-Path $candidate) { return $candidate }
    return $null
}

# Returns the command (array: exe + leading args) of a working Python 3.8+ or $null.
# The Microsoft Store "python" alias stub fails the version probe and is skipped.
function Find-Python {
    $candidates = @(@('py', '-3'), @('python'), @('python3'))
    foreach ($c in $candidates) {
        $exe = $c[0]
        if (-not (Get-Command $exe -ErrorAction SilentlyContinue)) { continue }
        $rest = @()
        if ($c.Count -gt 1) { $rest = $c[1..($c.Count - 1)] }
        $probe = $rest + @('-c', 'import sys; print(1 if sys.version_info >= (3, 8) else 0)')
        $out = $null
        try { $out = & $exe @probe 2>$null } catch { $out = $null }
        if ("$out".Trim() -eq '1') { return , $c }
    }
    return $null
}

# Reads KEY=VALUE lines of _Deployment/docker/.env into a hashtable (comments and blank lines ignored).
function Read-EnvFile {
    $values = @{}
    if (-not (Test-Path $script:EnvFile)) { return $values }
    foreach ($line in Get-Content -LiteralPath $script:EnvFile) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $i = $t.IndexOf('=')
        if ($i -lt 1) { continue }
        $key = $t.Substring(0, $i).Trim()
        $val = $t.Substring($i + 1).Trim()
        if ($val.Length -ge 2 -and (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'")))) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        $values[$key] = $val
    }
    return $values
}

function New-RandomHex([int]$Bytes = 32) {
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return (($buffer | ForEach-Object { $_.ToString('x2') }) -join '')
}

# Polls a URL until it answers 200 or the timeout expires.
function Wait-Http([string]$Url, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Invoke-Compose {
    # docker compose, always run from _Deployment\docker so .env and docker-compose.yml are picked up.
    Push-Location $script:DockerDir
    try {
        & docker compose @args | Out-Host
        return $LASTEXITCODE
    } finally {
        Pop-Location
    }
}

function Write-Summary {
    Write-Host ''
    Write-Host '============================================================' -ForegroundColor Green
    Write-Host ' Guest Desk is running' -ForegroundColor Green
    Write-Host '============================================================' -ForegroundColor Green
    Write-Host '  Web panel     : http://localhost'
    Write-Host '  API health    : http://localhost:8080/api/health'
    Write-Host ''
    Write-Host '  Demo accounts (also on the "Demo quick login" of the sign-in screen):'
    Write-Host '    Platform admin : admin@guestdesk.io / Admin@GuestDesk1'
    Write-Host '    Test owner     : owner@guestdesk.io / Owner@GuestDesk1'
    Write-Host ''
    Write-Host '  In the _Deployment folder:'
    Write-Host '    Stop (keeps the data)        : manage.bat down'
    Write-Host '    Reset (DELETES the database) : manage.bat reset   then run start-all.bat again'
    Write-Host '    Logs                         : manage.bat logs'
    Write-Host '============================================================' -ForegroundColor Green
}
