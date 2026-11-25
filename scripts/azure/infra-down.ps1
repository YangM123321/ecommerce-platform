# Deletes everything Terraform created (cluster, database WITH DATA, registry, images, storage),
# so it stops costing money. Run it at the end of every session; infra-up.ps1 brings it back.
# The small Terraform-state storage (rg-ecommerce-tfstate) is kept on purpose - cents a month.
#
#   powershell -ExecutionPolicy Bypass -File scripts\azure\infra-down.ps1

param([switch]$Force)

. (Join-Path $PSScriptRoot "common.ps1")
Assert-Tool "terraform" "Install Terraform: winget install -e --id Hashicorp.Terraform"
Assert-AzureLogin

Write-Host "This destroys rg-ecommerce: AKS, PostgreSQL WITH ALL DATA, the registry and its images," -ForegroundColor Yellow
Write-Host "and the storage account with all product images." -ForegroundColor Yellow
if (-not $Force) {
    $answer = Read-Host "Type DELETE to continue"
    if ($answer -ne "DELETE") { Write-Host "Cancelled."; exit 0 }
}

Push-Location $TfDir
try {
    Invoke-Step "terraform init" { terraform init -input=false -backend-config=backend.hcl }
    Write-Title "terraform destroy (about 10-15 minutes)"
    Invoke-Step "terraform destroy" { terraform destroy -input=false -auto-approve }
} finally { Pop-Location }

Invoke-Quietly { kubectl config delete-context aks-ecommerce } | Out-Null
Write-Host ""
Write-Host "Destroyed. Check: az group list --output table   (rg-ecommerce gone; rg-ecommerce-tfstate stays)" -ForegroundColor Green
