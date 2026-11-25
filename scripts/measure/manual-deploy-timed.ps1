# Session 6 BASELINE: time one full manual deploy, the way it was done before the pipeline
# (docs/manual-deploy.md): run all tests, build + push all 6 images, helm-deploy all 6, smoke test.
# Run it 3 times (infrastructure up, Docker running, on main, pushed). Paste each printed row
# into docs/deploy-time.md.
#
#   powershell -ExecutionPolicy Bypass -File scripts\measure\manual-deploy-timed.ps1

. (Join-Path $PSScriptRoot "..\azure\common.ps1")
Set-Location $RepoRoot
$total = [System.Diagnostics.Stopwatch]::StartNew()

Write-Title "1/3 All tests: mvn verify"
$t1 = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-Step "mvn verify" { mvn -B -q verify } | Out-Null
$t1.Stop()

Write-Title "2/3 Build, push and deploy all 6 services (deploy.ps1)"
$t2 = [System.Diagnostics.Stopwatch]::StartNew()
& (Join-Path $PSScriptRoot "..\azure\deploy.ps1")
$t2.Stop()

Write-Title "3/3 Smoke tests against Azure"
$t3 = [System.Diagnostics.Stopwatch]::StartNew()
$o = Get-TfOutputs
$ip = Get-IngressIp
& (Join-Path $RepoRoot "scripts\smoke-test.ps1") -BaseUrl "http://$ip" -AdminEmail $o.admin_email.value -AdminPassword $o.admin_password.value
& (Join-Path $RepoRoot "scripts\smoke-test-orders.ps1") -BaseUrl "http://$ip" -AdminEmail $o.admin_email.value -AdminPassword $o.admin_password.value
$t3.Stop()
$total.Stop()

$commit = (git rev-parse --short HEAD).Trim()
$f = { param($sw) "{0:mm\:ss}" -f $sw.Elapsed }
Write-Host ""
Write-Host "Paste this row into docs/deploy-time.md (Manual table):" -ForegroundColor Green
Write-Host ("| {0} | {1} | {2} | {3} | {4} | **{5}** |" -f (Get-Date -Format "yyyy-MM-dd HH:mm"), $commit,
    (& $f $t1), (& $f $t2), (& $f $t3), (& $f $total))
Write-Host "Hands-on time: all of it - you started each step and watched it." -ForegroundColor DarkGray
