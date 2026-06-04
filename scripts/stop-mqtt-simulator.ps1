$ErrorActionPreference = "SilentlyContinue"

$publishers = Get-CimInstance Win32_Process |
    Where-Object { $_.CommandLine -like "*mqtt-publisher.js*" -or $_.CommandLine -like "*mqtt-broker.js*" -or $_.CommandLine -like "*mqtt-publisher-loop.ps1*" }
foreach ($process in $publishers) {
    Stop-Process -Id $process.ProcessId -Force
}

$brokerConnection = Get-NetTCPConnection -LocalPort 1883 | Select-Object -First 1
if ($brokerConnection) {
    Stop-Process -Id $brokerConnection.OwningProcess -Force
}

Write-Host "MQTT simulator stopped"
