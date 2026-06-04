$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$logsDir = Join-Path $root "logs"

function Stop-PortProcess([int]$Port) {
    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($connections) {
        $connections | ForEach-Object {
            Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
        }
        Write-Host "Stopped listener on port $Port"
    } else {
        Write-Host "Port $Port is not listening"
    }
}

function Stop-PidFile([string]$Name) {
    $file = Join-Path $logsDir "$Name.pid"
    if (-not (Test-Path $file)) {
        return
    }
    $pidText = (Get-Content -Raw $file).Trim()
    if ($pidText -match '^\d+$') {
        Stop-Process -Id ([int]$pidText) -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped $Name pid $pidText"
    }
    Remove-Item -Force $file -ErrorAction SilentlyContinue
}

Stop-PidFile "mqtt-simulator"
Stop-PidFile "mqtt-broker"
Stop-PidFile "opcua-simulator"

Stop-PortProcess 1883
Stop-PortProcess 4840

Write-Host "Simulators stopped."
