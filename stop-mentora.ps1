<#
.SYNOPSIS
    Stops the local Mentora backend and website dev processes that were
    started by start-mentora.ps1. Never touches MongoDB, and never touches
    any process this script did not itself start (PID-tracked, with a
    port-ownership check to guard against stale/reused PIDs).

.NOTES
    If script execution is blocked by PowerShell's execution policy, run once:
        powershell -ExecutionPolicy Bypass -File .\stop-mentora.ps1
#>

$ErrorActionPreference = 'Continue'

$RepoRoot   = $PSScriptRoot
$RuntimeDir = Join-Path $RepoRoot '.local-runtime'

$BackendStateFile = Join-Path $RuntimeDir 'backend.json'
$WebStateFile     = Join-Path $RuntimeDir 'web.json'

function Write-Section([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-PortOwnerPid([int]$Port) {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($conn) { return $conn.OwningProcess }
    return $null
}

# Stops one tracked service described by its state file, verifying the
# tracked PID is still alive AND still the process actually listening on the
# expected port before killing it - this is the safety check that keeps this
# script from ever touching an unrelated process that happens to reuse a PID.
function Stop-TrackedService([string]$Name, [string]$StateFile) {
    if (-not (Test-Path $StateFile)) {
        Write-Host "$Name : no tracked process (nothing to stop)."
        return
    }

    $state = Get-Content -Path $StateFile -Raw | ConvertFrom-Json
    $appPid = $state.appPid
    $windowPid = $state.windowPid
    $port = $state.port

    $proc = Get-Process -Id $appPid -ErrorAction SilentlyContinue
    $currentOwner = Get-PortOwnerPid -Port $port

    if (-not $proc) {
        Write-Host "$Name : tracked process (PID $appPid) is no longer running."
    } elseif ($currentOwner -ne $appPid) {
        Write-Host "$Name : tracked PID $appPid is no longer the process listening on port $port - not killing it (likely a stale/reused PID)." -ForegroundColor Yellow
    } else {
        try {
            Stop-Process -Id $appPid -Force -ErrorAction Stop
            Write-Host "$Name : stopped process PID $appPid (was listening on port $port)."
        } catch {
            Write-Host "$Name : failed to stop PID $appPid - $($_.Exception.Message)" -ForegroundColor Red
        }
    }

    # Best-effort: also close the wrapper PowerShell window, if it's still open.
    if ($windowPid) {
        $windowProc = Get-Process -Id $windowPid -ErrorAction SilentlyContinue
        if ($windowProc) {
            Stop-Process -Id $windowPid -Force -ErrorAction SilentlyContinue
        }
    }

    Remove-Item -Path $StateFile -Force -ErrorAction SilentlyContinue
}

Write-Section "Stopping backend"
Stop-TrackedService -Name "Backend" -StateFile $BackendStateFile

Write-Section "Stopping website"
Stop-TrackedService -Name "Website" -StateFile $WebStateFile

Write-Host ""
Write-Host "MongoDB was left running (not stopped by this script)." -ForegroundColor DarkGray
Write-Host ""
Write-Host "MENTORA LOCAL ENVIRONMENT STOPPED" -ForegroundColor Green
Write-Host ""
