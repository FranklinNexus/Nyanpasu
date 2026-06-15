# 一次性配置 GitHub Actions 签名 Secrets（需先 gh auth login）
param(
    [string]$Repo = "FranklinNexus/Nyanpasu"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$gh = if (Get-Command gh -ErrorAction SilentlyContinue) {
    "gh"
} elseif (Test-Path "${env:ProgramFiles}\GitHub CLI\gh.exe") {
    "${env:ProgramFiles}\GitHub CLI\gh.exe"
} else {
    Write-Error "请先安装 GitHub CLI: winget install GitHub.cli"
}

& $gh auth status 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "请先登录: gh auth login" -ForegroundColor Yellow
    exit 1
}

$propsFile = Join-Path $Root "keystore.properties"
if (-not (Test-Path $propsFile)) {
    Write-Error "找不到 $propsFile（请从 keystore.properties.example 复制并填写）"
}

$props = @{}
Get-Content $propsFile | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $props[$Matches[1].Trim()] = $Matches[2].Trim() }
}

$storeFile = $props["storeFile"]
if ([string]::IsNullOrWhiteSpace($storeFile)) {
    Write-Error "keystore.properties 缺少 storeFile"
}

$jks = if ([System.IO.Path]::IsPathRooted($storeFile)) {
    $storeFile
} else {
    Join-Path $Root $storeFile
}

if (-not (Test-Path $jks)) {
    Write-Error "找不到签名文件: $jks"
}

Write-Host ">> 使用 keystore: $jks" -ForegroundColor Cyan

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jks))

& $gh secret set KEYSTORE_BASE64 --repo $Repo --body $b64
& $gh secret set KEYSTORE_PASSWORD --repo $Repo --body $props["storePassword"]
& $gh secret set KEY_ALIAS --repo $Repo --body $props["keyAlias"]
& $gh secret set KEY_PASSWORD --repo $Repo --body $props["keyPassword"]

Write-Host "OK: GitHub Secrets 已配置。重新触发 Release:" -ForegroundColor Green
Write-Host "  git tag -f v1.0.0"
Write-Host "  git push origin v1.0.0 --force"
