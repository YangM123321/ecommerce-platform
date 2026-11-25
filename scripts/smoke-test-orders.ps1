# Session 3 smoke test: catalog + orders against the app running in Docker.
# Usage (from the project folder, with `docker compose up --build` running):
#   powershell -ExecutionPolicy Bypass -File scripts\smoke-test-orders.ps1

$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"

function Send-Json($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $params = @{ Method = $method; Uri = "$base$path"; Headers = $headers; ContentType = "application/json" }
    if ($null -ne $body) { $params["Body"] = ($body | ConvertTo-Json -Depth 5) }
    Invoke-RestMethod @params
}

function Get-StatusCode($errorRecord) {
    [int]$errorRecord.Exception.Response.StatusCode
}

function Get-Stock($productId) {
    (Send-Json Get "/api/products/$productId" $null $null).available
}

Write-Host "`n1. Browse the catalog without logging in (first 3 products, cheapest first)" -ForegroundColor Cyan
$page = Send-Json Get "/api/products?size=3&sort=price,asc" $null $null
$page.content | Format-Table id, sku, name, price, categoryName, available
Write-Host "   Page $($page.page + 1) of $($page.totalPages), $($page.totalElements) products in total"

Write-Host "`n2. Admin creates a product with 3 in stock" -ForegroundColor Cyan
$admin = (Send-Json Post "/api/auth/login" @{ email = "admin@local.test"; password = "Admin123!" } $null).accessToken
$categoryId = (Send-Json Get "/api/categories" $null $null)[0].id
$product = Send-Json Post "/api/products" @{
    sku = "SMOKE-$(Get-Random)"; name = "Smoke Test Lamp"; description = "created by smoke test"
    price = 19.99; categoryId = $categoryId; initialStock = 3
} $admin
Write-Host "   Created product $($product.id) '$($product.name)' with stock $($product.available)"

Write-Host "`n3. A customer registers, logs in and orders 2" -ForegroundColor Cyan
$email = "shopper$(Get-Random)@test.local"
Send-Json Post "/api/auth/register" @{ email = $email; password = "Password123"; fullName = "Smoke Shopper" } $null | Out-Null
$user = (Send-Json Post "/api/auth/login" @{ email = $email; password = "Password123" } $null).accessToken
$order = Send-Json Post "/api/orders" @{ items = @(@{ productId = $product.id; quantity = 2 }) } $user
Write-Host "   Order $($order.id): status $($order.status), total $($order.totalAmount)"
Write-Host "   Stock is now $(Get-Stock $product.id) (expected 1)"

Write-Host "`n4. Try to order 5 more (only 1 left, expect 409)" -ForegroundColor Cyan
try { Send-Json Post "/api/orders" @{ items = @(@{ productId = $product.id; quantity = 5 }) } $user | Out-Null; Write-Host "   UNEXPECTED: allowed" -ForegroundColor Red }
catch { Write-Host "   Got $(Get-StatusCode $_) as expected" -ForegroundColor Green }

Write-Host "`n5. A normal user tries to create a product (expect 403)" -ForegroundColor Cyan
try { Send-Json Post "/api/products" @{ sku = "NOPE-$(Get-Random)"; name = "Nope"; price = 1; categoryId = $categoryId; initialStock = 1 } $user | Out-Null; Write-Host "   UNEXPECTED: allowed" -ForegroundColor Red }
catch { Write-Host "   Got $(Get-StatusCode $_) as expected" -ForegroundColor Green }

Write-Host "`n6. My orders" -ForegroundColor Cyan
(Send-Json Get "/api/orders" $null $user).content | Format-Table id, status, totalAmount, createdAt

Write-Host "7. Cancel the order, stock comes back" -ForegroundColor Cyan
$cancelled = Send-Json Post "/api/orders/$($order.id)/cancel" $null $user
Write-Host "   Order $($cancelled.id): status $($cancelled.status)"
Write-Host "   Stock is now $(Get-Stock $product.id) (expected 3)"

Write-Host "`nSmoke test finished." -ForegroundColor Green
