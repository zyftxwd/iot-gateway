param(
    [string]$RepoPath = "iot-gateway",
    [string]$RepoName = "",
    [string]$Description = "Industrial IoT gateway platform: devices, protocol acquisition, alarms, work orders, maintenance cards, reports and permissions.",
    [string]$ImportUrl = "https://github.com/zyftxwd/iot-gateway.git",
    [switch]$Private
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

function Resolve-GitExecutable {
    $gitCommand = Get-Command git -ErrorAction SilentlyContinue
    if ($gitCommand) {
        return $gitCommand.Source
    }

    $candidates = @(
        "$env:ProgramFiles\Git\cmd\git.exe",
        "${env:ProgramFiles(x86)}\Git\cmd\git.exe"
    )

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return $candidate
        }
    }

    throw "Git executable was not found. Install Git or add it to PATH."
}

function Invoke-GiteeApi {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Body = $null,
        [int]$TimeoutSec = 60
    )

    $encodedToken = [System.Uri]::EscapeDataString($script:GiteeToken)
    $uri = "https://gitee.com/api/v5$Path"
    if ($uri.Contains("?")) {
        $uri = "$uri&access_token=$encodedToken"
    } else {
        $uri = "$uri`?access_token=$encodedToken"
    }

    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -TimeoutSec $TimeoutSec
    }

    return Invoke-RestMethod `
        -Method $Method `
        -Uri $uri `
        -Body $Body `
        -ContentType "application/x-www-form-urlencoded; charset=utf-8" `
        -TimeoutSec $TimeoutSec
}

if ([string]::IsNullOrWhiteSpace($RepoName)) {
    # "工业物联网网关平台". Kept as code points so Windows PowerShell 5.1 can parse the file safely.
    $RepoName = New-StringFromCodePoints @(24037, 19994, 29289, 32852, 32593, 32593, 20851, 24179, 21488)
}

$git = Resolve-GitExecutable

Write-Host "This script will create or reuse a Gitee repository and configure the local 'gitee' remote."
Write-Host "Repository display name: $RepoName"
Write-Host "Repository path: $RepoPath"
Write-Host "Do not paste your token into chat. Paste it only into this PowerShell prompt."

$secureToken = Read-Host "Paste Gitee access token" -AsSecureString
$script:GiteeToken = ConvertFrom-SecureStringPlain -Secure $secureToken
$secureToken = $null

try {
    if ([string]::IsNullOrWhiteSpace($script:GiteeToken)) {
        throw "Gitee token is required."
    }

    $user = Invoke-GiteeApi -Method "GET" -Path "/user" -TimeoutSec 30
    $owner = $user.login
    if ([string]::IsNullOrWhiteSpace($owner)) {
        $owner = $user.name
    }
    if ([string]::IsNullOrWhiteSpace($owner)) {
        throw "Cannot resolve Gitee user path from token."
    }

    $repo = $null
    try {
        $repo = Invoke-GiteeApi -Method "GET" -Path "/repos/$owner/$RepoPath" -TimeoutSec 30
        Write-Host "Gitee repository already exists: $($repo.html_url)"
    } catch {
        $createBody = @{
            name = $RepoPath
            path = $RepoPath
            human_name = $RepoName
            description = $Description
            private = ([bool]$Private).ToString().ToLowerInvariant()
            has_issues = "true"
            has_wiki = "true"
            auto_init = "false"
            import_url = $ImportUrl
        }

        $repo = Invoke-GiteeApi -Method "POST" -Path "/user/repos" -Body $createBody -TimeoutSec 90
        Write-Host "Created Gitee repository: $($repo.html_url)"
    }

    $remoteUrl = "https://gitee.com/$owner/$RepoPath.git"
    $existing = ""
    try {
        $existing = & $git remote get-url gitee 2>$null
    } catch {
        $existing = ""
    }

    if ([string]::IsNullOrWhiteSpace($existing)) {
        & $git remote add gitee $remoteUrl
    } else {
        & $git remote set-url gitee $remoteUrl
    }

    Write-Host "Gitee remote configured: $remoteUrl"
    Write-Host "If Gitee did not import the GitHub repository automatically, run:"
    Write-Host "  git push gitee main"
    Write-Host "When Git asks for a password, use your Gitee access token as the password."
} finally {
    $script:GiteeToken = $null
}
