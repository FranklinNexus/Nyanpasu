# Windows 下 processDebugResources / R.jar 删不掉时使用
# 用法：先完全关闭 Android Studio，再在 PowerShell 运行：
#   .\scripts\win-clean-build.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

Write-Host ">> 停止 Gradle Daemon..."
& .\gradlew.bat --stop 2>$null

Start-Sleep -Seconds 2

$buildDir = Join-Path $Root "app\build"
if (Test-Path $buildDir) {
    Write-Host ">> 删除 app\build ..."
    Remove-Item -Recurse -Force $buildDir
}

Write-Host ">> 开始 assembleDebug ..."
& .\gradlew.bat assembleDebug --no-daemon

if ($LASTEXITCODE -eq 0) {
    $apk = Get-ChildItem "app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($apk) { Write-Host "OK: $($apk.FullName)" -ForegroundColor Green }
} else {
    Write-Host "构建仍失败。请确认 Android Studio 已完全关闭后重试。" -ForegroundColor Red
    exit 1
}
