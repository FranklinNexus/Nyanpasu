# 一次性配置 GitHub Actions 签名 Secrets（需先 gh auth login）
param(
    [string]$Repo = "FranklinNexus/Nyanpasu"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Error "请先安装 GitHub CLI: winget install GitHub.cli"
}

gh auth status 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "请先登录: gh auth login" -ForegroundColor Yellow
    exit 1
}

$props = @{}
Get-Content "keystore.properties" | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $props[$Matches[1]] = $Matches[2] }
}

$jks = $props["storeFile"]
if (-not (Test-Path $jks)) { Write-Error "找不到 $jks" }

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jks))

gh secret set KEYSTORE_BASE64 --repo $Repo --body $b64
gh secret set KEYSTORE_PASSWORD --repo $Repo --body $props["storePassword"]
gh secret set KEY_ALIAS --repo $Repo --body $props["keyAlias"]
gh secret set KEY_PASSWORD --repo $Repo --body $props["keyPassword"]

Write-Host "OK: GitHub Secrets 已配置。到 Actions 重新运行 Release Build，或:" -ForegroundColor Green
Write-Host "  git push --force origin v1.0.0"
