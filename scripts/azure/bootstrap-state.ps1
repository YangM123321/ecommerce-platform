# Session 6: one-time home for Terraform's state file (called by infra-up.ps1; safe to re-run).
#
# Terraform remembers what it created in a "state" file. Kept on your PC it would be lost with the
# PC and the pipeline couldn't read it, so it goes in a small storage account of its own, in its own
# resource group. "terraform destroy" never touches this group. Cost: a few cents a month.
#
# Also creates (first run only) and commits-ready:
#   infra/terraform/azure/terraform.tfvars  - your unique name suffix + region (not secret)
#   infra/terraform/azure/backend.hcl       - where the state lives (not secret)

. (Join-Path $PSScriptRoot "common.ps1")

$tfvars = Join-Path $TfDir "terraform.tfvars"
$backend = Join-Path $TfDir "backend.hcl"

if (-not (Test-Path $tfvars)) {
    $suffix = New-RandomText 6
    @(
        "# Created by scripts/azure/bootstrap-state.ps1. Keep the suffix: Azure names depend on it.",
        "suffix   = ""$suffix""",
        "location = ""centralus"""
    ) | Set-Content -Path $tfvars -Encoding ASCII
    Write-Host "Created terraform.tfvars with suffix $suffix"
}
$suffix = ([regex]::Match((Get-Content $tfvars -Raw), 'suffix\s*=\s*"([a-z0-9]+)"')).Groups[1].Value
$location = ([regex]::Match((Get-Content $tfvars -Raw), 'location\s*=\s*"([a-z0-9]+)"')).Groups[1].Value
if (-not $location) { $location = "centralus" }

$stateRg = "rg-ecommerce-tfstate"
$stateAccount = "sttfecom$suffix"

Write-Title "Terraform state storage: $stateAccount in $stateRg"
Invoke-Step "state resource group" { az group create --name $stateRg --location $location --output none }
if (Invoke-Quietly { az storage account show --resource-group $stateRg --name $stateAccount --output none; "yes" }) {
    Write-Host "  already exists"
} else {
    Invoke-Step "state storage account" {
        az storage account create --resource-group $stateRg --name $stateAccount --location $location `
            --sku Standard_LRS --kind StorageV2 --min-tls-version TLS1_2 --allow-blob-public-access false --output none
    }
}
$key = (Invoke-Step "storage key" {
    az storage account keys list --resource-group $stateRg --account-name $stateAccount --query "[0].value" --output tsv
}).Trim()
Invoke-Step "state container" {
    az storage container create --name tfstate --account-name $stateAccount --account-key $key --output none
}

@(
    "# Created by scripts/azure/bootstrap-state.ps1 - where Terraform keeps its state.",
    "resource_group_name  = ""$stateRg""",
    "storage_account_name = ""$stateAccount""",
    "container_name       = ""tfstate""",
    "key                  = ""ecommerce.tfstate"""
) | Set-Content -Path $backend -Encoding ASCII
Write-Host "backend.hcl written."
