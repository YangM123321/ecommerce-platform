# What's running on AKS right now, and the public URL.
#   powershell -ExecutionPolicy Bypass -File scripts\azure\status.ps1

. (Join-Path $PSScriptRoot "common.ps1")
Assert-AzureLogin
$o = Get-TfOutputs

Invoke-Step "helm list" { helm list --namespace $Namespace }
Invoke-Step "kubectl get" { kubectl get pods,services,ingress --namespace $Namespace }
$ip = Get-IngressIp
if ($ip) { Write-Host "`nApp: http://$ip/   Admin: $($o.admin_email.value) / $($o.admin_password.value)" -ForegroundColor Green }
Write-Host "Logs of one service:  kubectl logs deployment/product-service -n ecommerce --tail=100"
