<#
.SYNOPSIS
  Guest Desk, step 2 of 3: builds the images and starts the complete Docker deployment.

.DESCRIPTION
  Runs "docker compose up --build -d" in _Deployment\docker (PostgreSQL 16, Ktor backend, nginx + web panel),
  then waits until the backend answers on http://localhost:8080/api/health and through nginx on
  http://localhost/api/health. On failure it prints the container status and the backend log.
  The first build takes about 3-5 minutes; later builds reuse the Docker and Gradle caches.

.PARAMETER TimeoutSeconds
  How long to wait for the health checks after the containers have started (default 180).
#>
param([int]$TimeoutSeconds = 180)

. (Join-Path $PSScriptRoot 'common.ps1')

Write-Host 'Guest Desk - step 2/3: build and start' -ForegroundColor Cyan

if (-not (Test-Path $script:EnvFile)) {
    Stop-WithError '_Deployment\docker\.env does not exist. Run steps\01-check-dependencies.bat (or start-all.bat) first.'
}
if (-not (Test-Command 'docker info')) {
    Stop-WithError 'The Docker engine is not running. Start Docker Desktop (or run steps\01-check-dependencies.bat) and try again.'
}

# ---------------------------------------------------------------- ports
Write-Step 'Ports 80, 8080 and 5432'
$running = @(& docker ps -q --filter 'label=com.docker.compose.project=guestdesk')
$ownPorts = @{}
if ($running.Count -gt 0) {
    foreach ($line in (& docker ps --filter 'label=com.docker.compose.project=guestdesk' --format '{{.Ports}}')) {
        foreach ($p in 80, 8080, 5432) { if ($line -match ":$p->") { $ownPorts[$p] = $true } }
    }
}
$busy = @()
foreach ($port in 80, 8080, 5432) {
    if ($ownPorts.ContainsKey($port)) { continue }
    $conn = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conn) {
        $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
        $name = if ($proc) { $proc.ProcessName } else { "PID $($conn.OwningProcess)" }
        $busy += "port $port is used by $name"
    }
}
if ($busy.Count -gt 0) {
    $other = @(& docker ps --format '{{.Names}} ({{.Ports}})' | Where-Object { $_ -match ':(80|8080|5432)->' -and $_ -notmatch '^guestdesk-' })
    $hint = ''
    if ($other.Count -gt 0) { $hint = " Other running containers publish these ports: $($other -join '; '). Stop them with: docker stop <name>." }
    Stop-WithError ("Ports needed by Guest Desk are in use: " + ($busy -join ', ') + ".$hint Free the ports and run again.")
}
Write-Ok 'The ports are free (or already used by this stack).'

# ---------------------------------------------------------------- build + start
Write-Step 'docker compose up --build -d   (first run: about 3-5 minutes)'
$started = Get-Date
$code = Invoke-Compose up --build -d
if ($code -ne 0) {
    Write-Host ''
    Write-Warn 'docker compose up failed. Container status and the last backend log lines:'
    Invoke-Compose ps -a | Out-Null
    Invoke-Compose logs --tail 60 backend | Out-Null
    Stop-WithError "docker compose up --build -d failed (exit code $code). See the messages above."
}
$elapsed = [int]((Get-Date) - $started).TotalSeconds
Write-Ok "Containers started in $elapsed s."

# ---------------------------------------------------------------- health
Write-Step 'Health checks'
if (-not (Wait-Http 'http://localhost:8080/api/health' $TimeoutSeconds)) {
    Invoke-Compose ps -a | Out-Null
    Invoke-Compose logs --tail 80 backend | Out-Null
    Stop-WithError "The backend did not answer on http://localhost:8080/api/health within $TimeoutSeconds s (log above)."
}
Write-Ok 'Backend: http://localhost:8080/api/health -> 200'
if (-not (Wait-Http 'http://localhost/api/health' 60)) {
    Invoke-Compose ps -a | Out-Null
    Invoke-Compose logs --tail 40 frontend | Out-Null
    Stop-WithError 'The web panel (nginx) did not answer on http://localhost/api/health (log above).'
}
Write-Ok 'Web panel and API proxy: http://localhost/api/health -> 200'

Write-Host ''
Write-Host 'Step 2/3 done: the stack is running.' -ForegroundColor Green
exit 0
