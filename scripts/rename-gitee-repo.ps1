param(
    [string]$Owner = "zhangyifan123456",
    [string]$RepoPath = "iot-gateway",
    [string]$DisplayName = "",
    [string]$Description = "Industrial IoT gateway platform: devices, protocol acquisition, alarms, work orders, maintenance cards, reports and permissions."
)

$ErrorActionPreference = "Stop"

function New-StringFromCodePoints {
    param([int[]]$CodePoints)

    $builder = New-Object System.Text.StringBuilder
    foreach ($codePoint in $CodePoints) {
        [void]$builder.Append([char]$codePoint)
    }
    return $builder.ToString()
}

function ConvertFrom-SecureStringPlain {
    param([System.Security.SecureString]$Secure)

    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Invoke-GiteePatch {
    param(
        [string]$Token,
        [hashtable]$Body
    )

    $encodedToken = [System.Uri]::EscapeDataString($Token)
    $uri = "https://gitee.com/api/v5/repos/$Owner/$RepoPath`?access_token=$encodedToken"

    return Invoke-RestMethod `
        -Method "PATCH" `
        -Uri $uri `
        -Body $Body `
        -ContentType "application/x-www-form-urlencoded; charset=utf-8" `
        -TimeoutSec 60
}

if ([string]::IsNullOrWhiteSpace($DisplayName)) {
    # "工业物联网网关平台". Kept as code points so Windows PowerShell 5.1 can parse the file safely.
    $DisplayName = New-StringFromCodePoints @(24037, 19994, 29289, 32852, 32593, 32593, 20851, 24179, 21488)
}

Write-Host "This script will update the Gitee repository display name."
Write-Host "Repository: https://gitee.com/$Owner/$RepoPath"
Write-Host "Display name: $DisplayName"
Write-Host "Do not paste your token into chat. Paste it only into this PowerShell prompt."

$secureToken = Read-Host "Paste Gitee access token" -AsSecureString
$token = ConvertFrom-SecureStringPlain -Secure $secureToken
$secureToken = $null

try {
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Gitee token is required."
    }

    # Gitee API versions differ on the visible repository title field. Try the non-path
    # display fields first; keep path/name as the existing URL path to avoid breaking Git remotes.
    $attempts = @(
        @{ human_name = $DisplayName; path = $RepoPath; name = $RepoPath; description = $Description },
        @{ project_name = $DisplayName; path = $RepoPath; name = $RepoPath; description = $Description },
        @{ display_name = $DisplayName; path = $RepoPath; name = $RepoPath; description = $Description }
    )

    $lastError = $null
    foreach ($body in $attempts) {
        try {
            $repo = Invoke-GiteePatch -Token $token -Body $body
            Write-Host "Updated Gitee repository:"
            Write-Host "  URL: $($repo.html_url)"
            Write-Host "  name: $($repo.name)"
            Write-Host "  human_name: $($repo.human_name)"
            Write-Host "  path: $($repo.path)"
            return
        } catch {
            $lastError = $_
        }
    }

    throw $lastError
} finally {
    $token = $null
}
