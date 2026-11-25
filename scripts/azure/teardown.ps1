# Deletes EVERYTHING this project created in Azure, so it stops costing money.
# Run it at the end of every session. setup-azure.ps1 + deploy.ps1 bring it all back (~35 min).
#
#   powershell -ExecutionPolicy Bypass -File scripts\azure\teardown.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\azure\teardown.ps1 -Force   (no question)

param([switch]$Force)

. (Join-Path $PSScriptRoot "common.ps1")
$e = Read-AzureEnv
$rg = $e.resourceGroup

$exists = (az group exists --name $rg).Trim()
if ($exists -ne "true") {
    Write-Host "Resource group $rg doesn't exist - nothing to delete."
} else {
    Write-Host "This deletes resource group ${rg}: the AKS cluster, the PostgreSQL server WITH ALL DATA," -ForegroundColor Yellow
    Write-Host "the container registry with its images, and the storage account with all product images." -ForegroundColor Yellow
    if (-not $Force) {
        $answer = Read-Host "Type DELETE to continue"
        if ($answer -ne "DELETE") { Write-Host "Cancelled."; exit 0 }
    }
    # One delete removes everything, because setup put everything in this one group.
    # (AKS's own MC_... node group is deleted automatically with the cluster.)
    Invoke-Step "delete resource group" { az group delete --name $rg --yes --no-wait }
    Write-Host "Deletion started. It finishes in about 10 minutes."
}

Invoke-Quietly { kubectl config delete-context $e.aks } | Out-Null
# New names next time: Azure keeps some deleted names reserved for a while.
Remove-Item $EnvFile -ErrorAction SilentlyContinue
Write-Host "Removed .azure-env.json. Next session, setup-azure.ps1 makes fresh names."
Write-Host "Check it's gone (wait ~10 min):  az group list --output table" -ForegroundColor Cyan
