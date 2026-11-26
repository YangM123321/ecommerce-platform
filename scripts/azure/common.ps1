# Shared helpers for the Azure scripts. Not run on its own.
# Since Session 6 the Azure resources come from Terraform (infra/terraform/azure);
# these scripts read names and secrets from "terraform output".

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$TfDir = Join-Path $RepoRoot "infra\terraform\azure"
$Namespace = "ecommerce"

# Deploy order: platform services first (the others read config / register on start).
$Modules = @("config-server", "discovery-server", "user-service", "product-service", "order-service", "api-gateway")

# Runs a native command (az, docker, kubectl, helm, terraform, git) and stops if it fails.
function Invoke-Step([string]$What, [scriptblock]$Command) {
    $output = & $Command
    if ($LASTEXITCODE -ne 0) { throw "$What failed (exit code $LASTEXITCODE). Read the error above." }
    return $output
}

# Runs a command whose failure is OK. Returns $null instead of stopping.
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

function Assert-Tool([string]$Name, [string]$Hint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { throw "'$Name' not found. $Hint" }
}

function New-RandomText([int]$Length, [string]$Alphabet = "abcdefghijkmnopqrstuvwxyz23456789") {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] $Length
    $rng.GetBytes($bytes)
    -join ($bytes | ForEach-Object { $Alphabet[$_ % $Alphabet.Length] })
}

# Checks "az login" and exports ARM_SUBSCRIPTION_ID for Terraform's azurerm provider.
function Assert-AzureLogin {
    $id = Invoke-Quietly { az account show --query id --output tsv }
    if (-not $id) {
        Write-Host "You're not logged in to Azure. In cmd, run:  az login   then run this script again." -ForegroundColor Yellow
        exit 1
    }
    $env:ARM_SUBSCRIPTION_ID = ($id -join "").Trim()
    $name = (az account show --query name --output tsv)
    Write-Host "Azure subscription: $name ($env:ARM_SUBSCRIPTION_ID)"
}

# terraform output -json -> object; use (Get-TfOutputs).acr_login_server.value
function Get-TfOutputs {
    Push-Location $TfDir
    try {
        if (-not (Test-Path ".terraform")) {
            Invoke-Step "terraform init" { terraform init -input=false "-backend-config=backend.hcl" } | Out-Null
        }
        $json = Invoke-Step "terraform output" { terraform output -json }
    } finally { Pop-Location }
    $outputs = ($json -join "`n") | ConvertFrom-Json
    if (-not $outputs.resource_group) {
        throw "No Terraform outputs - the infrastructure isn't up. Run scripts\azure\infra-up.ps1 first."
    }
    return $outputs
}

function Get-IngressIp {
    return kubectl get ingress api-gateway --namespace $Namespace --output "jsonpath={.status.loadBalancer.ingress[0].ip}"
}
