$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $root "backend"
$frontendDir = Join-Path $root "frontend"
$logsDir = Join-Path $root "logs"

New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

function Stop-PortProcess([int]$Port) {
    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($connections) {
        $connections | ForEach-Object {
            Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Seconds 2
    }
}

function Save-Pid([string]$Name, [int]$ProcessId) {
    Set-Content -Path (Join-Path $logsDir "$Name.pid") -Value $ProcessId -Encoding ASCII
}

function Resolve-Maven() {
    $candidates = @(
        "C:\Program Files\Apache\apache-maven-3.9.9\bin\mvn.cmd",
        "C:\Program Files\Apache\apache-maven-3.9.8\bin\mvn.cmd",
        "mvn.cmd",
        "mvn"
    )
    foreach ($candidate in $candidates) {
        $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($cmd) {
            return $cmd.Source
        }
    }
    throw "Maven was not found. Please install Maven or add mvn.cmd to PATH."
}

function Start-LoggedProcess([string]$Name, [string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory) {
    $stdout = Join-Path $logsDir "$Name.log"
    $stderr = Join-Path $logsDir "$Name.err.log"
    $process = Start-Process -FilePath $FilePath `
        -ArgumentList $Arguments `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru
    Save-Pid $Name $process.Id
    return $process
}

Write-Host "Stopping old backend/frontend..."
Stop-PortProcess 8080
Stop-PortProcess 5173

$maven = Resolve-Maven

Write-Host "Starting backend..."
Start-LoggedProcess "backend" $maven @("spring-boot:run") $backendDir | Out-Null

Write-Host "Starting frontend..."
Start-LoggedProcess "frontend" "npm.cmd" @("run", "dev", "--", "--host", "127.0.0.1", "--port", "5173") $frontendDir | Out-Null

Start-Sleep -Seconds 10

Write-Host ""
Write-Host "Backend/frontend are starting."
Write-Host "Frontend: http://127.0.0.1:5173"
Write-Host "Backend:  http://127.0.0.1:8080"
Write-Host "Swagger:  http://127.0.0.1:8080/swagger-ui/index.html"
Write-Host "Logs:     $logsDir"
Write-Host ""
Write-Host "Stop command: .\scripts\stop-dev.ps1"
