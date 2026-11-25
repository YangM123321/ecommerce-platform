# Session 5: give every product a picture, then check one loads straight from Blob Storage.
# Works locally (Azurite) and on AKS (Azure Blob Storage).
#
# Local (docker compose up running):
#   powershell -ExecutionPolicy Bypass -File scripts\upload-demo-images.ps1
# AKS (values printed by deploy.ps1 / status.ps1):
#   powershell -ExecutionPolicy Bypass -File scripts\upload-demo-images.ps1 -BaseUrl http://<IP> -AdminEmail admin@ecommerce.demo -AdminPassword <password>

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminEmail = "admin@local.test",
    [string]$AdminPassword = "Admin123!",
    [switch]$Replace   # also replace images that already exist
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Net.Http
$BaseUrl = $BaseUrl.TrimEnd("/")

# Draws a simple 600x450 PNG: a coloured background with the product name on it.
function New-ProductPicture([string]$Name, [int]$Seed, [string]$Path) {
    $colors = @("#2f6fde", "#0e9f6e", "#d97706", "#7c3aed", "#db2777", "#0891b2", "#4b5563", "#b91c1c")
    $bitmap = New-Object System.Drawing.Bitmap 600, 450
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.SmoothingMode = "AntiAlias"
    $g.TextRenderingHint = "AntiAliasGridFit"
    $rect = New-Object System.Drawing.Rectangle 0, 0, 600, 450
    $c1 = [System.Drawing.ColorTranslator]::FromHtml($colors[$Seed % $colors.Length])
    $c2 = [System.Drawing.Color]::FromArgb(255, [int]($c1.R * 0.55), [int]($c1.G * 0.55), [int]($c1.B * 0.55))
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, $c1, $c2, 45
    $g.FillRectangle($brush, $rect)
    $font = New-Object System.Drawing.Font "Segoe UI", 34, ([System.Drawing.FontStyle]::Bold)
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = "Center"
    $format.LineAlignment = "Center"
    $textArea = New-Object System.Drawing.RectangleF 40, 40, 520, 370
    $g.DrawString($Name, $font, [System.Drawing.Brushes]::White, $textArea, $format)
    $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bitmap.Dispose(); $brush.Dispose(); $font.Dispose()
}

Write-Host "`n1. Log in as admin at $BaseUrl" -ForegroundColor Cyan
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" `
    -Body (@{ email = $AdminEmail; password = $AdminPassword } | ConvertTo-Json)
$client = New-Object System.Net.Http.HttpClient
$client.DefaultRequestHeaders.Authorization =
    [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $login.accessToken)

Write-Host "`n2. Upload a picture for each product" -ForegroundColor Cyan
$products = (Invoke-RestMethod -Uri "$BaseUrl/api/products?size=50&sort=id").content
$lastUrl = $null
foreach ($p in $products) {
    if ($p.imageUrl -and -not $Replace) {
        Write-Host ("   {0,-28} already has an image" -f $p.name)
        $lastUrl = $p.imageUrl
        continue
    }
    $file = Join-Path $env:TEMP "product-$($p.id).png"
    New-ProductPicture $p.name $p.id $file

    # Multipart form upload, field name "file" (Windows PowerShell 5 has no -Form, so HttpClient).
    $content = [System.Net.Http.MultipartFormDataContent]::new()
    $bytes = [System.Net.Http.ByteArrayContent]::new([System.IO.File]::ReadAllBytes($file))
    $bytes.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new("image/png")
    $content.Add($bytes, "file", "product-$($p.id).png")
    $response = $client.PostAsync("$BaseUrl/api/products/$($p.id)/image", $content).Result
    $body = $response.Content.ReadAsStringAsync().Result
    if (-not $response.IsSuccessStatusCode) {
        throw "Upload for product $($p.id) failed: HTTP $([int]$response.StatusCode) $body"
    }
    $lastUrl = ($body | ConvertFrom-Json).imageUrl
    Write-Host ("   {0,-28} -> {1}" -f $p.name, $lastUrl) -ForegroundColor Green
    Remove-Item $file
}

Write-Host "`n3. Load one image straight from storage (no token, not through our services)" -ForegroundColor Cyan
if ($lastUrl) {
    $image = Invoke-WebRequest -Uri $lastUrl -UseBasicParsing
    Write-Host "   HTTP $($image.StatusCode), $($image.Headers['Content-Type']), $($image.RawContentLength) bytes" -ForegroundColor Green
}

Write-Host "`nDone. Open $BaseUrl/ in your browser to see the products with their pictures." -ForegroundColor Cyan
