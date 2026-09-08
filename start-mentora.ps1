<#
.SYNOPSIS
    One-command startup for the local Mentora development environment
    (MongoDB check, backend, website), with health-check-based readiness.

.NOTES
    If script execution is blocked by PowerShell's execution policy, run once:
        powershell -ExecutionPolicy Bypass -File .\start-mentora.ps1
    or for the current user permanently:
        Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
#>

$ErrorActionPreference = 'Stop'

$RepoRoot   = $PSScriptRoot
$BackendDir = Join-Path $RepoRoot 'backend'
$WebDir     = Join-Path $RepoRoot 'web'
$RuntimeDir = Join-Path $RepoRoot '.local-runtime'

$BackendStateFile = Join-Path $RuntimeDir 'backend.json'
$WebStateFile     = Join-Path $RuntimeDir 'web.json'

$BackendPort = 8080
$WebPort     = 3000
$BackendHealthUrl = "http://localhost:$BackendPort/healthz"
$WebHealthUrl     = "http://localhost:$WebPort/en"

$ReadyTimeoutSec = 120
$PollIntervalSec = 2

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

function Write-Section([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Test-HttpHealthy([string]$Url) {
    try {
        $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
        return ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400)
    } catch {
        return $false
    }
}

function Get-PortOwnerPid([int]$Port) {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($conn) { return $conn.OwningProcess }
    return $null
}

function Save-State([string]$Path, [hashtable]$State) {
    ($State | ConvertTo-Json) | Set-Content -Path $Path -Encoding utf8
}

# Returns $true if a service this script previously started (tracked via its
# state file) - or any other process - is already listening on $Port / serving
# $HealthUrl. Does not distinguish "ours" from "someone else's manual run";
# either way we must not start a duplicate.
function Test-AlreadyRunning([int]$Port, [string]$HealthUrl) {
    if (Get-PortOwnerPid -Port $Port) { return $true }
    return (Test-HttpHealthy -Url $HealthUrl)
}

function Start-DevWindow([string]$Title, [string]$WorkDir, [string]$Command) {
    $psCommand = "`$Host.UI.RawUI.WindowTitle = '$Title'; Set-Location -LiteralPath '$WorkDir'; $Command"
    return Start-Process powershell.exe -ArgumentList @('-NoExit', '-Command', $psCommand) -WindowStyle Normal -PassThru
}

# ---------------------------------------------------------------------------
# 0. Runtime state dir
# ---------------------------------------------------------------------------

New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null

# ---------------------------------------------------------------------------
# 1. MongoDB service
# ---------------------------------------------------------------------------

Write-Section "Checking MongoDB service"

$mongoSvc = Get-Service -Name 'MongoDB' -ErrorAction SilentlyContinue
if (-not $mongoSvc) {
    $mongoSvc = Get-Service -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -like '*MongoDB*' } | Select-Object -First 1
}

if (-not $mongoSvc) {
    Write-Host "MongoDB Windows service not found. Ensure MongoDB Community is installed as a native" -ForegroundColor Yellow
    Write-Host "Windows service (see backend/README.md) rather than run manually or via Docker." -ForegroundColor Yellow
} elseif ($mongoSvc.Status -eq 'Running') {
    Write-Host "MongoDB service '$($mongoSvc.Name)' is already running."
} else {
    Write-Host "MongoDB service '$($mongoSvc.Name)' is stopped. Attempting to start it..."
    try {
        Start-Service -Name $mongoSvc.Name -ErrorAction Stop
        Start-Sleep -Seconds 2
        $mongoSvc.Refresh()
        if ($mongoSvc.Status -eq 'Running') {
            Write-Host "MongoDB service started."
        } else {
            Write-Host "MongoDB service did not reach the 'Running' state. Check it manually (services.msc)." -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host ""
        Write-Host "Could not start the MongoDB service - this most likely requires administrator" -ForegroundColor Red
        Write-Host "privileges. Open PowerShell as Administrator and run:" -ForegroundColor Red
        Write-Host "    Start-Service '$($mongoSvc.Name)'" -ForegroundColor Red
        Write-Host "or start it via services.msc ('MongoDB Server')." -ForegroundColor Red
        Write-Host "Details: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}

# ---------------------------------------------------------------------------
# 2. Replica set health
# ---------------------------------------------------------------------------

Write-Section "Checking MongoDB replica set health"

$mongoshCmd = Get-Command mongosh -ErrorAction SilentlyContinue
if (-not $mongoshCmd) {
    Write-Host "mongosh not found on PATH - skipping replica set health check." -ForegroundColor Yellow
    Write-Host "(The backend will still fail transactional requests if the replica set isn't healthy -" -ForegroundColor Yellow
    Write-Host " see backend/README.md 'One-time MongoDB setup'.)" -ForegroundColor Yellow
} else {
    $rsOutput = (& mongosh --quiet --eval "try { print('STATE:' + rs.status().myState) } catch (e) { print('ERR:' + e.message) }" 2>&1 | Out-String).Trim()
    if ($rsOutput -match 'STATE:1') {
        Write-Host "Replica set is healthy (PRIMARY)."
    } else {
        Write-Host ""
        Write-Host "MongoDB replica set is not healthy or not initialized:" -ForegroundColor Red
        Write-Host "    $rsOutput" -ForegroundColor Red
        Write-Host "See backend/README.md 'One-time MongoDB setup: convert to a single-node replica set'." -ForegroundColor Red
        exit 1
    }
}

# ---------------------------------------------------------------------------
# 3-6. Start backend + website (skip if already running)
# ---------------------------------------------------------------------------

Write-Section "Backend"
$backendAlreadyRunning = Test-AlreadyRunning -Port $BackendPort -HealthUrl $BackendHealthUrl
$backendWindowPid = $null
if ($backendAlreadyRunning) {
    Write-Host "Backend already running on port $BackendPort - skipping start (not touching it)."
} else {
    Write-Host "Starting backend (.\gradlew.bat run) from $BackendDir in a new window..."
    $proc = Start-DevWindow -Title 'Mentora Backend' -WorkDir $BackendDir -Command '.\gradlew.bat run'
    $backendWindowPid = $proc.Id
}

Write-Section "Website"
$webAlreadyRunning = Test-AlreadyRunning -Port $WebPort -HealthUrl $WebHealthUrl
$webWindowPid = $null
if ($webAlreadyRunning) {
    Write-Host "Website already running on port $WebPort - skipping start (not touching it)."
} else {
    Write-Host "Starting website (npm run dev) from $WebDir in a new window..."
    $proc = Start-DevWindow -Title 'Mentora Website' -WorkDir $WebDir -Command 'npm run dev'
    $webWindowPid = $proc.Id
}

# ---------------------------------------------------------------------------
# 7. Wait until both services are actually reachable
# ---------------------------------------------------------------------------

Write-Section "Waiting for services to become reachable (timeout: ${ReadyTimeoutSec}s)"

$deadline = (Get-Date).AddSeconds($ReadyTimeoutSec)
$backendReady = $false
$webReady = $false

while ((Get-Date) -lt $deadline -and -not ($backendReady -and $webReady)) {
    if (-not $backendReady) { $backendReady = Test-HttpHealthy -Url $BackendHealthUrl }
    if (-not $webReady)     { $webReady     = Test-HttpHealthy -Url $WebHealthUrl }

    if ($backendReady -and $webReady) { break }

    Write-Host ("  backend: {0,-8} website: {1,-8}" -f `
        $(if ($backendReady) {'ready'} else {'waiting'}), `
        $(if ($webReady) {'ready'} else {'waiting'}))
    Start-Sleep -Seconds $PollIntervalSec
}

# Now that services (if freshly started) are confirmed reachable, resolve the
# *actual* listening process for PID tracking. This deliberately targets the
# real backend/frontend process rather than the wrapper window, because
# `gradlew run` executes the application under the Gradle daemon, not as a
# direct child of the launching shell - killing the shell window would not
# stop the server.
if ($backendReady -and -not $backendAlreadyRunning) {
    $appPid = Get-PortOwnerPid -Port $BackendPort
    if ($appPid) {
        Save-State -Path $BackendStateFile -State @{ appPid = $appPid; windowPid = $backendWindowPid; port = $BackendPort }
    }
}
if ($webReady -and -not $webAlreadyRunning) {
    $appPid = Get-PortOwnerPid -Port $WebPort
    if ($appPid) {
        Save-State -Path $WebStateFile -State @{ appPid = $appPid; windowPid = $webWindowPid; port = $WebPort }
    }
}

if (-not ($backendReady -and $webReady)) {
    Write-Host ""
    Write-Host "Timed out waiting for services to become ready." -ForegroundColor Red
    if (-not $backendReady) { Write-Host "  Backend never responded at $BackendHealthUrl - check the 'Mentora Backend' window." -ForegroundColor Red }
    if (-not $webReady)     { Write-Host "  Website never responded at $WebHealthUrl - check the 'Mentora Website' window." -ForegroundColor Red }
    exit 1
}

# ---------------------------------------------------------------------------
# 8. Open the site in the default browser
# ---------------------------------------------------------------------------

Start-Process $WebHealthUrl

# ---------------------------------------------------------------------------
# 9. Final status
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "MENTORA LOCAL ENVIRONMENT READY" -ForegroundColor Green
Write-Host ""
Write-Host "Backend:  http://localhost:$BackendPort"
Write-Host "Website:  $WebHealthUrl"
Write-Host ""
