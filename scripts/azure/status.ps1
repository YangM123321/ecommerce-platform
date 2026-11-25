# What's running on AKS right now, and the public URL.
#   powershell -ExecutionPolicy Bypass -File scripts\azure\status.ps1

. (Join-Path $PSScriptRoot "common.ps1")
$e = Read-AzureEnv

Invoke-Step "kubectl get" { kubectl get pods,services,ingress --namespace ecommerce }
$ip = kubectl get ingress api-gateway --namespace ecommerce --output "jsonpath={.status.loadBalancer.ingress[0].ip}"
if ($ip) { Write-Host "`nApp: http://$ip/   Admin: $($e.adminEmail) / $($e.adminPassword)" -ForegroundColor Green }
Write-Host "Logs of one service:  kubectl logs deployment/product-service -n ecommerce --tail=100"
