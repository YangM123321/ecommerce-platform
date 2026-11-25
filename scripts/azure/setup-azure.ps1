# Session 5, step 1: create the Azure resources (one time per session, ~20-25 minutes).
#
#   powershell -ExecutionPolicy Bypass -File scripts\azure\setup-azure.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\azure\setup-azure.ps1 -Location westus2
#
# Creates, all in ONE resource group (so teardown.ps1 can delete everything in one go):
#   Azure Container Registry (Basic)          - stores the 6 Docker images
#   AKS cluster, 1 node (Standard_B2ms)        - runs the containers, with managed NGINX ingress
#   PostgreSQL Flexible Server (Burstable B1ms) - users_db, catalog_db, orders_db
#   Storage account                            - product images (Blob Storage)
#
# COSTS MONEY while it exists. Run teardown.ps1 at the end of every session.

param(
    [string]$Location = "eastus2",
    [string]$NodeSize = "Standard_B2ms"
)

. (Join-Path $PSScriptRoot "common.ps1")

Assert-Tool "az" "Install the Azure CLI: winget install -e --id Microsoft.AzureCLI (then open a NEW cmd window)."
Assert-Tool "kubectl" "Docker Desktop includes kubectl; or run: az aks install-cli"

Write-Title "Checking you are logged in to Azure"
$account = Invoke-Quietly { az account show } | ConvertFrom-Json
if (-not $account) {
    Invoke-Step "az login" { az login } | Out-Null
    $account = Invoke-Step "az account show" { az account show } | ConvertFrom-Json
}
Write-Host "Subscription: $($account.name) ($($account.id))"

# Unique names: ACR, storage and PostgreSQL names must be unique across ALL of Azure.
if (Test-Path $EnvFile) {
    $envValues = Read-AzureEnv
    Write-Host "Reusing names from .azure-env.json (suffix $($envValues.suffix))"
} else {
    $suffix = New-RandomText 6 "abcdefghijkmnopqrstuvwxyz23456789"
    $envValues = [pscustomobject]@{
        suffix          = $suffix
        location        = $Location
        resourceGroup   = "rg-ecommerce"
        acr             = "acrecom$suffix"
        aks             = "aks-ecommerce"
        postgres        = "pg-ecom-$suffix"
        storage         = "stecom$suffix"
        dbUser          = "ecomadmin"
        dbPassword      = "Pg-" + (New-RandomText 24)
        jwtSecret       = New-RandomText 48
        adminEmail      = "admin@ecommerce.demo"
        adminPassword   = "Adm-" + (New-RandomText 16)
    }
    Save-AzureEnv $envValues
    Write-Host "New names saved to .azure-env.json (suffix $suffix). Keep this file private."
}
$rg = $envValues.resourceGroup
$loc = $envValues.location

Write-Title "Registering Azure resource providers (first time only, can take a few minutes)"
foreach ($ns in "Microsoft.ContainerService", "Microsoft.ContainerRegistry", "Microsoft.DBforPostgreSQL", "Microsoft.Storage") {
    Invoke-Step "register $ns" { az provider register --namespace $ns --wait } | Out-Null
    Write-Host "  $ns registered"
}

Write-Title "Resource group $rg in $loc"
Invoke-Step "create resource group" { az group create --name $rg --location $loc --output none }

Write-Title "Container registry $($envValues.acr) (~1 min)"
Invoke-Step "create ACR" { az acr create --resource-group $rg --name $envValues.acr --sku Basic --output none }

Write-Title "Storage account $($envValues.storage) for product images (~1 min)"
# --allow-blob-public-access: lets the product-images container allow anonymous READ of single
# images, so browsers can load them by URL. Writing still needs the key.
Invoke-Step "create storage account" {
    az storage account create --resource-group $rg --name $envValues.storage --location $loc `
        --sku Standard_LRS --kind StorageV2 --min-tls-version TLS1_2 `
        --allow-blob-public-access true --output none
}

Write-Title "PostgreSQL Flexible Server $($envValues.postgres) (~5-10 min)"
# --public-access 0.0.0.0 = firewall rule "allow Azure services", so AKS can connect.
# Stretch goal later: private networking (VNet integration) instead.
Invoke-Step "create PostgreSQL server" {
    az postgres flexible-server create --resource-group $rg --name $envValues.postgres --location $loc `
        --tier Burstable --sku-name Standard_B1ms --storage-size 32 --version 16 `
        --admin-user $envValues.dbUser --admin-password $envValues.dbPassword `
        --public-access 0.0.0.0 --yes --output none
}
foreach ($db in "users_db", "catalog_db", "orders_db") {
    Invoke-Step "create database $db" {
        az postgres flexible-server db create --resource-group $rg --server-name $envValues.postgres `
            --database-name $db --output none
    }
    Write-Host "  database $db created"
}

Write-Title "AKS cluster $($envValues.aks), 1 x $NodeSize (~5-10 min)"
# --attach-acr: the cluster may pull images from our registry without passwords.
# --enable-app-routing: managed NGINX ingress controller with a public IP.
Invoke-Step "create AKS" {
    az aks create --resource-group $rg --name $envValues.aks --location $loc `
        --tier free --node-count 1 --node-vm-size $NodeSize `
        --attach-acr $envValues.acr --enable-app-routing --generate-ssh-keys --output none
}
Invoke-Step "get AKS credentials" {
    az aks get-credentials --resource-group $rg --name $envValues.aks --overwrite-existing
}
Invoke-Step "kubectl get nodes" { kubectl get nodes }

Write-Title "Done. Next: powershell -ExecutionPolicy Bypass -File scripts\azure\deploy.ps1"
Write-Host "Remember: this costs money while it exists. End the session with teardown.ps1." -ForegroundColor Yellow
