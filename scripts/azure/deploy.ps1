# Deploy BY HAND from your PC: build images, push them to ACR, "helm upgrade" each service, check it works.
# This is the "manual" deploy that Session 6 times against the pipeline (docs/deploy-time.md).
#
#   powershell -ExecutionPolicy Bypass -File scripts\azure\deploy.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\azure\deploy.ps1 -Services product-service
#
# Needs: infra-up.ps1 done, Docker Desktop running, your branch pushed to GitHub
# (config-server reads config-repo/ from GitHub at the branch you deploy).

param([string[]]$Services = @())

. (Join-Path $PSScriptRoot "common.ps1")
Set-Location $RepoRoot
Assert-Tool "docker" "Install and start Docker Desktop."
Assert-Tool "helm" "Install Helm: winget install -e --id Helm.Helm"
$timer = [System.Diagnostics.Stopwatch]::StartNew()

if ($Services.Count -eq 0) { $Services = $Modules }
$Services = $Modules | Where-Object { $Services -contains $_ }   # keep the safe order

Write-Title "Checking git"
$branch = (Invoke-Step "git branch" { git rev-parse --abbrev-ref HEAD }).Trim()
$commit = (Invoke-Step "git commit" { git rev-parse --short HEAD }).Trim()
# A new tag every run, so Kubernetes always rolls out (a real deploy of a new build).
$tag = "$commit-m$(Get-Date -Format 'MMddHHmmss')"
if (git status --porcelain) {
    Write-Host "WARNING: uncommitted changes. Images use your files, but config-server reads GitHub." -ForegroundColor Yellow
}
$remote = Invoke-Quietly { git rev-parse --verify --quiet "origin/$branch" }
if (-not $remote) { throw "Branch '$branch' isn't on GitHub yet. Run: git push -u origin $branch" }
Write-Host "Branch $branch, image tag $tag, services: $($Services -join ', ')"

Assert-AzureLogin
$o = Get-TfOutputs
$login = $o.acr_login_server.value

Write-Title "Building and pushing images to $login"
Invoke-Step "az acr login" { az acr login --name $o.acr_name.value }
foreach ($m in $Services) {
    Write-Host "-- $m" -ForegroundColor Cyan
    Invoke-Step "docker build $m" { docker build --build-arg "MODULE=$m" -t "${login}/${m}:${tag}" . }
    Invoke-Step "docker push $m" { docker push "${login}/${m}:${tag}" }
}

Write-Title "Helm upgrade (one release per service; waits until each is ready)"
Invoke-Step "get AKS credentials" {
    az aks get-credentials --resource-group $o.resource_group.value --name $o.aks_name.value --overwrite-existing
}
foreach ($m in $Services) {
    Write-Host "-- $m" -ForegroundColor Cyan
    $helmArgs = @("upgrade", "--install", $m, "charts/service", "--namespace", $Namespace,
        "--values", "deploy/values/$m.yaml",
        "--set", "image.repository=$login/$m", "--set", "image.tag=$tag",
        "--wait", "--timeout", "10m")
    if ($m -eq "config-server") { $helmArgs += @("--set", "extraEnv.CONFIG_GIT_LABEL=$branch") }
    Invoke-Step "helm upgrade $m" { helm @helmArgs }
}

Write-Title "Checking it works"
$ip = ""
for ($i = 0; $i -lt 60 -and -not $ip; $i++) {
    $ip = Get-IngressIp
    if (-not $ip) { Start-Sleep -Seconds 5 }
}
if (-not $ip) { throw "No public IP on the ingress yet. Check: kubectl get ingress -n $Namespace" }
$ok = $false
for ($i = 0; $i -lt 30 -and -not $ok; $i++) {
    try {
        $health = Invoke-RestMethod -Uri "http://$ip/actuator/health" -TimeoutSec 10
        $products = Invoke-RestMethod -Uri "http://$ip/api/products?size=1" -TimeoutSec 10
        $ok = ($health.status -eq "UP") -and ($products.totalElements -ge 1)
    } catch { $ok = $false }
    if (-not $ok) { Start-Sleep -Seconds 10 }
}
if (-not $ok) { throw "Smoke check failed: http://$ip/actuator/health or /api/products didn't answer correctly." }
Write-Host "Health UP, products API answers." -ForegroundColor Green

$timer.Stop()
Write-Host ""
Write-Host ("Deployed {0} service(s) in {1:mm\:ss} (mm:ss)" -f $Services.Count, $timer.Elapsed) -ForegroundColor Green
Write-Host "Your app:      http://$ip/"
Write-Host "Admin login:   $($o.admin_email.value) / $($o.admin_password.value)"
Write-Host "End of the day: scripts\azure\infra-down.ps1" -ForegroundColor Yellow
