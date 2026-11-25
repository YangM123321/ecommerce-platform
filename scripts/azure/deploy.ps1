# Session 5, step 2: build the 6 images, push them to ACR and deploy them to AKS (~10-15 minutes).
# Run again after every code change; each commit gets its own image tag.
#
#   powershell -ExecutionPolicy Bypass -File scripts\azure\deploy.ps1
#
# Needs: setup-azure.ps1 done, Docker Desktop running, and your branch PUSHED to GitHub
# (on AKS the config server reads config-repo/ from GitHub at the branch you deploy).

. (Join-Path $PSScriptRoot "common.ps1")
Set-Location $RepoRoot

Assert-Tool "docker" "Install and start Docker Desktop."
$e = Read-AzureEnv
$rg = $e.resourceGroup

Write-Title "Checking git"
$branch = (Invoke-Step "git branch" { git rev-parse --abbrev-ref HEAD }).Trim()
$tag = (Invoke-Step "git commit id" { git rev-parse --short HEAD }).Trim()
if (git status --porcelain) {
    Write-Host "WARNING: you have uncommitted changes. Images are built from your files, but the" -ForegroundColor Yellow
    Write-Host "         config server reads config-repo/ from GitHub. Commit and push first." -ForegroundColor Yellow
}
$remote = Invoke-Quietly { git rev-parse --verify --quiet "origin/$branch" }
if (-not $remote) {
    throw "Branch '$branch' isn't on GitHub yet. Run: git push -u origin $branch"
}
if ($remote.Trim() -ne (git rev-parse HEAD).Trim()) {
    Write-Host "WARNING: '$branch' has commits that aren't pushed. Run: git push" -ForegroundColor Yellow
}
Write-Host "Branch $branch, image tag $tag"

Write-Title "Logging in to the container registry"
$login = (Invoke-Step "ACR login server" { az acr show --name $e.acr --query loginServer --output tsv }).Trim()
Invoke-Step "az acr login" { az acr login --name $e.acr }

Write-Title "Building and pushing 6 images to $login"
foreach ($m in $Modules) {
    Write-Host "-- $m" -ForegroundColor Cyan
    Invoke-Step "docker build $m" { docker build --build-arg "MODULE=$m" -t "${login}/${m}:${tag}" . }
    Invoke-Step "docker push $m" { docker push "${login}/${m}:${tag}" }
}

Write-Title "Connecting kubectl to the cluster"
Invoke-Step "get AKS credentials" { az aks get-credentials --resource-group $rg --name $e.aks --overwrite-existing }

$dbHost = (Invoke-Step "PostgreSQL host" {
    az postgres flexible-server show --resource-group $rg --name $e.postgres --query fullyQualifiedDomainName --output tsv
}).Trim()
$storageConnection = (Invoke-Step "storage connection string" {
    az storage account show-connection-string --resource-group $rg --name $e.storage --query connectionString --output tsv
}).Trim()

# Fill in the placeholders in k8s/*.yaml and write the result to a temp folder.
$rendered = Join-Path $env:TEMP "ecommerce-k8s"
if (Test-Path $rendered) { Remove-Item $rendered -Recurse -Force }
New-Item -ItemType Directory $rendered | Out-Null
Get-ChildItem (Join-Path $RepoRoot "k8s") -Filter *.yaml | ForEach-Object {
    $text = (Get-Content $_.FullName -Raw).Replace("__ACR__", $login).Replace("__TAG__", $tag)
    $text = $text.Replace("__DB_HOST__", $dbHost).Replace("__GIT_LABEL__", $branch)
    Set-Content -Path (Join-Path $rendered $_.Name) -Value $text -Encoding ASCII
}
function Apply([string]$File) { Invoke-Step "kubectl apply $File" { kubectl apply -f (Join-Path $rendered $File) } }

Write-Title "Namespace, settings and secrets"
Apply "00-namespace.yaml"
Apply "01-configmap.yaml"
# The Secret is built from .azure-env.json and Azure - its values are never written to a file in the repo.
$secretYaml = Invoke-Step "build secret" {
    kubectl create secret generic ecommerce-secrets --namespace ecommerce --dry-run=client --output yaml `
        "--from-literal=DB_USERNAME=$($e.dbUser)" `
        "--from-literal=DB_PASSWORD=$($e.dbPassword)" `
        "--from-literal=JWT_SECRET=$($e.jwtSecret)" `
        "--from-literal=ADMIN_EMAIL=$($e.adminEmail)" `
        "--from-literal=ADMIN_PASSWORD=$($e.adminPassword)" `
        "--from-literal=STORAGE_CONNECTION_STRING=$storageConnection"
}
$secretFile = Join-Path $rendered "secret.generated"
Set-Content -Path $secretFile -Value ($secretYaml -join "`n") -Encoding ASCII
Invoke-Step "apply secret" { kubectl apply -f $secretFile }
Remove-Item $secretFile

Write-Title "Platform services first (config-server, discovery-server)"
Apply "10-config-server.yaml"
Apply "11-discovery-server.yaml"
foreach ($d in "config-server", "discovery-server") {
    Invoke-Step "rollout $d" { kubectl rollout status "deployment/$d" --namespace ecommerce --timeout=10m }
}

Write-Title "Business services, gateway and ingress"
Apply "20-user-service.yaml"
Apply "21-product-service.yaml"
Apply "22-order-service.yaml"
Apply "30-api-gateway.yaml"
Apply "40-ingress.yaml"
foreach ($d in "user-service", "product-service", "order-service", "api-gateway") {
    Invoke-Step "rollout $d" { kubectl rollout status "deployment/$d" --namespace ecommerce --timeout=10m }
}

Write-Title "Waiting for the public IP"
$ip = ""
for ($i = 0; $i -lt 60 -and -not $ip; $i++) {
    $ip = kubectl get ingress api-gateway --namespace ecommerce --output "jsonpath={.status.loadBalancer.ingress[0].ip}"
    if (-not $ip) { Start-Sleep -Seconds 5 }
}
Invoke-Step "kubectl get pods" { kubectl get pods --namespace ecommerce }

Write-Host ""
if ($ip) {
    Write-Host "Your app:      http://$ip/" -ForegroundColor Green
    Write-Host "Health:        http://$ip/actuator/health"
    Write-Host "Products API:  http://$ip/api/products"
} else {
    Write-Host "No public IP yet. Check in a minute: kubectl get ingress -n ecommerce" -ForegroundColor Yellow
}
Write-Host "Admin login:   $($e.adminEmail) / $($e.adminPassword)"
Write-Host "When you're done for the day: scripts\azure\teardown.ps1" -ForegroundColor Yellow
