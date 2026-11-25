# Shared helpers for the Azure scripts. Not run on its own.

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$EnvFile = Join-Path $RepoRoot ".azure-env.json"   # names + generated passwords; in .gitignore

$Modules = @("config-server", "discovery-server", "user-service", "product-service", "order-service", "api-gateway")

# Runs a native command (az, docker, kubectl, git) and stops the script if it fails.
# PowerShell doesn't stop on its own when a native program fails, so we check $LASTEXITCODE.
function Invoke-Step([string]$What, [scriptblock]$Command) {
    $output = & $Command
    if ($LASTEXITCODE -ne 0) { throw "$What failed (exit code $LASTEXITCODE). Read the error above." }
    return $output
}

# Runs a command whose failure is OK (e.g. "am I logged in?"). Returns $null instead of stopping.
function Invoke-Quietly([scriptblock]$Command) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try { $output = & $Command 2>$null } finally { $ErrorActionPreference = $previous }
    if ($LASTEXITCODE -ne 0) { return $null }
    return $output
}

function Write-Title([string]$Text) {
    Write-Host ""
    Write-Host "== $Text" -ForegroundColor Cyan
}

function New-RandomText([int]$Length, [string]$Alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789") {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] $Length
    $rng.GetBytes($bytes)
    -join ($bytes | ForEach-Object { $Alphabet[$_ % $Alphabet.Length] })
}

function Read-AzureEnv {
    if (-not (Test-Path $EnvFile)) {
        throw "No $EnvFile yet. Run scripts\azure\setup-azure.ps1 first."
    }
    Get-Content $EnvFile -Raw | ConvertFrom-Json
}

function Save-AzureEnv($Values) {
    $Values | ConvertTo-Json | Set-Content -Path $EnvFile -Encoding UTF8
}

function Assert-Tool([string]$Name, [string]$Hint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { throw "'$Name' not found. $Hint" }
}
