param(
    [string]$HostAddress = "127.0.0.1",
    [int]$Port = 1883,
    [string]$Topic = "iiot/test"
)

while ($true) {
    $temperature = [math]::Round(22 + [math]::Sin((Get-Date).Second / 6) * 4 + (Get-Random -Minimum -30 -Maximum 30) / 100, 2)
    $humidity = [math]::Round(55 + [math]::Cos((Get-Date).Second / 8) * 8 + (Get-Random -Minimum -50 -Maximum 50) / 100, 2)
    $pressure = [math]::Round(0.65 + (Get-Random -Minimum 0 -Maximum 80) / 100, 2)
    $speed = Get-Random -Minimum 1180 -Maximum 1520
    $running = ($speed -gt 1250)
    $payload = [ordered]@{
        temperature = $temperature
        humidity = $humidity
        pressure = $pressure
        status = if ($running) { "杩愯" } else { "寰呮満" }
        motor = [ordered]@{
            speed = $speed
            running = $running
            current = [math]::Round(8 + (Get-Random -Minimum 0 -Maximum 300) / 100, 2)
        }
        alarms = @(
            [ordered]@{ code = "A001"; active = ($temperature -gt 25); level = "WARN" },
            [ordered]@{ code = "P001"; active = ($pressure -gt 1.2); level = "INFO" }
        )
        timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    } | ConvertTo-Json -Depth 6 -Compress

    npx -y mqtt publish -h $HostAddress -p $Port -t $Topic -m $payload | Out-Null
    Start-Sleep -Seconds 1
}
