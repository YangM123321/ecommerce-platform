# Session 6, one time: create the 6 SonarCloud projects (one per service) the pipelines analyse into.
#
#   powershell -ExecutionPolicy Bypass -File scripts\sonar\create-projects.ps1 -Organization <org key> -Token <token>
#
# Organization key: SonarCloud -> your avatar -> My Organizations (e.g. yangm123321).
# Token: SonarCloud -> My Account -> Security -> Generate token.
param(
    [Parameter(Mandatory = $true)][string]$Organization,
    [Parameter(Mandatory = $true)][string]$Token,
    [string]$KeyPrefix = "YangM123321_ecommerce-"
)
$ErrorActionPreference = "Stop"
$auth = @{ Authorization = "Bearer $Token" }
$services = "config-server", "discovery-server", "api-gateway", "user-service", "product-service", "order-service"

foreach ($s in $services) {
    $key = "$KeyPrefix$s"
    try {
        Invoke-RestMethod -Method Post -Headers $auth -Uri "https://sonarcloud.io/api/projects/create" -Body @{
            organization = $Organization; project = $key; name = "ecommerce $s"; visibility = "public"
        } | Out-Null
        Write-Host "created  $key" -ForegroundColor Green
    } catch {
        $msg = $_.ErrorDetails.Message
        if ($msg -match "already exists") { Write-Host "exists   $key" }
        else { throw "Could not create ${key}: $msg" }
    }
}
Write-Host "`nIf your organization key isn't yangm123321, also change <sonar.organization> in pom.xml." -ForegroundColor Cyan
