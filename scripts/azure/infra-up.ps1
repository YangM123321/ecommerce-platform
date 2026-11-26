# Session 6: create ALL Azure resources with Terraform, then install the shared Kubernetes pieces.
# Replaces Session 5's setup-azure.ps1. About 15-20 minutes the first time.
#
#   powershell -ExecutionPolicy Bypass -File scripts\azure\infra-up.ps1
#
# Then deploy the services: scripts\azure\deploy.ps1 (by hand) or the Azure Pipelines (automatic).
# COSTS MONEY while it exists (~$4/day). End every session with scripts\azure\infra-down.ps1.

param([switch]$AutoApprove)

. (Join-Path $PSScriptRoot "common.ps1")

Assert-Tool "az" "Install the Azure CLI: winget install -e --id Microsoft.AzureCLI"
Assert-Tool "terraform" "Install Terraform: winget install -e --id Hashicorp.Terraform (then open a NEW cmd window)"
Assert-Tool "helm" "Install Helm: winget install -e --id Helm.Helm (then open a NEW cmd window)"
Assert-Tool "kubectl" "Docker Desktop includes kubectl; or run: az aks install-cli"

Write-Title "Azure login"
Assert-AzureLogin

Write-Title "Terraform state storage"
& (Join-Path $PSScriptRoot "bootstrap-state.ps1")

foreach ($ns in "Microsoft.ContainerService", "Microsoft.ContainerRegistry", "Microsoft.DBforPostgreSQL", "Microsoft.Storage") {
    Invoke-Step "register $ns" { az provider register --namespace $ns --wait } | Out-Null
}

Push-Location $TfDir
try {
    Write-Title "terraform init"
    Invoke-Step "terraform init" { terraform init -input=false "-backend-config=backend.hcl" }

    # Plan first and save it; apply exactly that plan. (Read it: "Plan: N to add, 0 to change, 0 to destroy".)
    Write-Title "terraform plan"
    Invoke-Step "terraform plan" { terraform plan -input=false -out=tfplan }
    if (-not $AutoApprove) {
        $answer = Read-Host "Create these resources? Type yes"
        if ($answer -ne "yes") { Write-Host "Cancelled - nothing was created."; exit 0 }
    }
    Write-Title "terraform apply (about 15 minutes: PostgreSQL and AKS are the slow ones)"
    Invoke-Step "terraform apply" { terraform apply -input=false tfplan }
    Remove-Item tfplan -ErrorAction SilentlyContinue
} finally { Pop-Location }

$o = Get-TfOutputs

Write-Title "Connecting kubectl to $($o.aks_name.value)"
Invoke-Step "get AKS credentials" {
    az aks get-credentials --resource-group $o.resource_group.value --name $o.aks_name.value --overwrite-existing
}
Invoke-Step "kubectl get nodes" { kubectl get nodes }

Write-Title "Shared Kubernetes pieces (Helm release 'platform': settings, secrets, ingress)"
# Secrets go through a temporary values file (deleted right after), never through the command line or Git.
$secretsFile = Join-Path $env:TEMP "ecommerce-platform-secrets.json"
@{
    dbHost  = $o.postgres_host.value
    secrets = @{
        DB_USERNAME               = $o.db_username.value
        DB_PASSWORD               = $o.db_password.value
        JWT_SECRET                = $o.jwt_secret.value
        ADMIN_EMAIL               = $o.admin_email.value
        ADMIN_PASSWORD            = $o.admin_password.value
        STORAGE_CONNECTION_STRING = $o.storage_connection_string.value
    }
} | ConvertTo-Json | Set-Content -Path $secretsFile -Encoding ASCII
try {
    Invoke-Step "helm platform" {
        helm upgrade --install platform (Join-Path $RepoRoot "charts\platform") `
            --namespace $Namespace --create-namespace --values $secretsFile
    }
} finally { Remove-Item $secretsFile -ErrorAction SilentlyContinue }

Write-Title "Done"
Write-Host "Registry:   $($o.acr_login_server.value)"
Write-Host "Next: deploy the services"
Write-Host "  by hand:   powershell -ExecutionPolicy Bypass -File scripts\azure\deploy.ps1"
Write-Host "  automatic: run the Azure Pipelines (docs/ci-cd.md)"
Write-Host "Remember: ~`$4/day while it exists. End the session with scripts\azure\infra-down.ps1" -ForegroundColor Yellow
