# Session 2 smoke test: runs the whole auth flow against the app running in Docker.
# Usage (from the project folder, with `docker compose up --build` running):
#   powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1

$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
$email = "demo$(Get-Random)@test.local"

function Post-Json($path, $body) {
    Invoke-RestMethod -Method Post -Uri "$base$path" -ContentType "application/json" -Body ($body | ConvertTo-Json)
}

function Get-Auth($path, $token) {
    Invoke-RestMethod -Uri "$base$path" -Headers @{ Authorization = "Bearer $token" }
}

function Get-StatusCode($errorRecord) {
    [int]$errorRecord.Exception.Response.StatusCode
}

Write-Host "`n1. Register $email" -ForegroundColor Cyan
Post-Json "/api/auth/register" @{ email = $email; password = "Password123"; fullName = "Demo User" } | Format-List

Write-Host "2. Login" -ForegroundColor Cyan
$tokens = Post-Json "/api/auth/login" @{ email = $email; password = "Password123" }
Write-Host "   accessToken: $($tokens.accessToken.Substring(0, 30))...  (expires in $($tokens.expiresIn)s)"

Write-Host "`n3. GET /api/users/me with the token" -ForegroundColor Cyan
Get-Auth "/api/users/me" $tokens.accessToken | Format-List

Write-Host "4. GET /api/users/me WITHOUT a token (expect 401)" -ForegroundColor Cyan
try { Invoke-RestMethod -Uri "$base/api/users/me" | Out-Null; Write-Host "   UNEXPECTED: allowed" -ForegroundColor Red }
catch { Write-Host "   Got $(Get-StatusCode $_) as expected" -ForegroundColor Green }

Write-Host "`n5. Admin endpoint as a normal user (expect 403)" -ForegroundColor Cyan
try { Get-Auth "/api/admin/users" $tokens.accessToken | Out-Null; Write-Host "   UNEXPECTED: allowed" -ForegroundColor Red }
catch { Write-Host "   Got $(Get-StatusCode $_) as expected" -ForegroundColor Green }

Write-Host "`n6. Admin endpoint as admin (admin@local.test from docker-compose.yml)" -ForegroundColor Cyan
$admin = Post-Json "/api/auth/login" @{ email = "admin@local.test"; password = "Admin123!" }
Get-Auth "/api/admin/users" $admin.accessToken | Format-Table id, email, roles

Write-Host "7. Refresh, then logout, then refresh again (expect 401)" -ForegroundColor Cyan
$newTokens = Post-Json "/api/auth/refresh" @{ refreshToken = $tokens.refreshToken }
Write-Host "   Refresh OK - got a new refresh token"
Post-Json "/api/auth/logout" @{ refreshToken = $newTokens.refreshToken } | Out-Null
Write-Host "   Logged out"
try { Post-Json "/api/auth/refresh" @{ refreshToken = $newTokens.refreshToken } | Out-Null; Write-Host "   UNEXPECTED: refresh after logout worked" -ForegroundColor Red }
catch { Write-Host "   Refresh after logout rejected with $(Get-StatusCode $_) as expected" -ForegroundColor Green }

Write-Host "`nSmoke test finished." -ForegroundColor Green
