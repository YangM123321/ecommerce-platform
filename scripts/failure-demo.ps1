# Session 4 deliverable: stop product-service and show the rest stays up.
# Usage (from the project folder, with `docker compose up --build -d` running and healthy):
#   powershell -ExecutionPolicy Bypass -File scripts\failure-demo.ps1

$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"

function Call($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $params = @{ Method = $method; Uri = "$base$path"; Headers = $headers; ContentType = "application/json" }
    if ($null -ne $body) { $params["Body"] = ($body | ConvertTo-Json -Depth 5) }
    try {
        $result = Invoke-RestMethod @params
        Write-Host "   -> OK" -ForegroundColor Green
        return $result
    } catch {
        $code = [int]$_.Exception.Response.StatusCode
        Write-Host "   -> HTTP $code" -ForegroundColor Yellow
        return $null
    }
}

function Wait-Status($path, $wanted, $token, $seconds) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-RestMethod -Uri "$base$path" -Headers $headers | Out-Null
            $code = 200
        } catch {
            $code = [int]$_.Exception.Response.StatusCode
        }
        if ($code -eq $wanted) { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

Write-Host "`n1. Everything running: log in, browse, place an order" -ForegroundColor Cyan
$email = "demo-$(Get-Random)@example.com"
Call Post "/api/auth/register" @{ email = $email; password = "Password123"; fullName = "Demo User" } $null | Out-Null
$token = (Call Post "/api/auth/login" @{ email = $email; password = "Password123" } $null).accessToken
Call Get "/api/products?size=1" $null $null | Out-Null
$order = Call Post "/api/orders" @{ items = @(@{ productId = 1; quantity = 1 }) } $token
Write-Host "   Order $($order.id) placed, status $($order.status)"

Write-Host "`n2. Stopping product-service..." -ForegroundColor Cyan
docker compose stop product-service | Out-Null
Write-Host "   Waiting for Eureka and the gateway to notice (up to 60 s)"
if (-not (Wait-Status "/api/products" 503 $null 60)) { Write-Host "   (still not 503 - carrying on)" }

Write-Host "`n3. With product-service DOWN:" -ForegroundColor Cyan
Write-Host " a. Browse products (needs product-service) - expect 503"
Call Get "/api/products" $null $null | Out-Null
Write-Host " b. Log in (user-service) - expect OK"
$token = (Call Post "/api/auth/login" @{ email = $email; password = "Password123" } $null).accessToken
Write-Host " c. My profile (user-service) - expect OK"
Call Get "/api/users/me" $null $token | Out-Null
Write-Host " d. My orders (order-service, its own database) - expect OK"
Call Get "/api/orders" $null $token | Out-Null
Write-Host " e. Place a new order (order-service must call product-service) - expect 503, clean error, no crash"
Call Post "/api/orders" @{ items = @(@{ productId = 1; quantity = 1 }) } $token | Out-Null

Write-Host "`n4. Starting product-service again..." -ForegroundColor Cyan
docker compose start product-service | Out-Null
Write-Host "   Waiting for it to register with Eureka (up to 120 s)"
if (Wait-Status "/api/products" 200 $null 120) {
    Write-Host " Browse products again - expect OK"
    Call Get "/api/products?size=1" $null $null | Out-Null
    # The gateway and order-service each keep their OWN copy of the Eureka registry,
    # refreshed every few seconds. So order-service may still think product-service is gone
    # for a few seconds after the gateway already sees it. Retry for up to 30 s.
    Write-Host " Place an order again - expect OK (order-service may need a few seconds to notice)"
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $result = Call Post "/api/orders" @{ items = @(@{ productId = 1; quantity = 1 }) } $token
        if ($result) { Write-Host "   Order $($result.id) placed, status $($result.status)"; break }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
} else {
    Write-Host "   product-service did not come back in time; check: docker compose logs product-service" -ForegroundColor Red
}
Write-Host "`nDone. Open http://localhost:8761 to see the Eureka dashboard." -ForegroundColor Cyan