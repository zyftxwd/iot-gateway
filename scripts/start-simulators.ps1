$ErrorActionPreference = "Stop"

param(
    [string]$MqttReadTopic = "iiot/test2",
    [string]$MqttWriteTopic = "iiot/write2"
)

$root = Split-Path -Parent $PSScriptRoot
$logsDir = Join-Path $root "logs"
$opcuaDir = Join-Path $root "opcua-test"

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

Write-Host "Stopping old simulators..."
Stop-PortProcess 1883
Stop-PortProcess 4840

Write-Host "Starting MQTT broker..."
Start-LoggedProcess "mqtt-broker" "node.exe" @(".\scripts\mqtt-broker.js", "127.0.0.1", "1883") $root | Out-Null

Start-Sleep -Seconds 2

Write-Host "Starting MQTT simulator..."
Start-LoggedProcess "mqtt-simulator" "node.exe" @(".\scripts\mqtt_simulator.js", "127.0.0.1", "1883", $MqttReadTopic, $MqttWriteTopic) $root | Out-Null

Write-Host "Starting OPC UA simulator..."
Start-LoggedProcess "opcua-simulator" "node.exe" @(".\opcua-server.js") $opcuaDir | Out-Null

Start-Sleep -Seconds 4

Write-Host ""
Write-Host "Simulators are starting."
Write-Host "MQTT Broker:   127.0.0.1:1883"
Write-Host "MQTT Read:     $MqttReadTopic"
Write-Host "MQTT Write:    $MqttWriteTopic"
Write-Host "OPC UA Server: opc.tcp://127.0.0.1:4840/UA/IiotTest"
Write-Host "OPC UA Point:  ns=1;s=Temperature"
Write-Host "Logs:          $logsDir"
Write-Host ""
Write-Host "Stop command:  .\scripts\stop-simulators.ps1"
