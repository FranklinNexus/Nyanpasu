# Nyanpasu Windows 构建脚本 — 与 Android Studio 分离输出目录
# Android Studio / gradlew：.out\app-ide / .out\app
# 本脚本：.out\app-<时间戳>，并复制到 .out\latest\app-debug.apk 或 app-release.apk
# 用法:
#   .\scripts\build.ps1              # assembleDebug
#   .\scripts\build.ps1 -Test        # 单测 + Debug APK
#   .\scripts\build.ps1 -Release     # assembleRelease
#   .\scripts\build.ps1 -Clean       # 清理 .out/app 与遗留 app\build 后再构建

param(
    [switch]$Clean,
    [switch]$Test,
    [switch]$Release
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$ApkSubPath = if ($Release) { "release" } else { "debug" }
$GradleTask = if ($Release) { "assembleRelease" } elseif ($Test) { "testDebugUnitTest assembleDebug" } else { "assembleDebug" }

function Stop-GradleRelatedProcesses {
    Write-Host ">> 停止 Gradle Daemon..."
    & .\gradlew.bat --stop 2>$null | Out-Null
    Start-Sleep -Seconds 1

    $patterns = @(
        "GradleDaemon",
        "org\.gradle\.launcher",
        "org\.gradle\.worker",
        "kotlin-compiler-daemon"
    )
    $regex = ($patterns -join "|")

    Get-CimInstance Win32_Process -Filter "name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and ($_.CommandLine -match $regex) } |
        ForEach-Object {
            Write-Host ">> 结束 Gradle 相关进程 PID $($_.ProcessId)"
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }

    Start-Sleep -Seconds 1
}

function Remove-DirWithRetry {
    param(
        [string]$Path,
        [int]$Retries = 10
    )

    if (-not (Test-Path $Path)) { return $true }

    for ($i = 0; $i -lt $Retries; $i++) {
        try {
            Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
            if (-not (Test-Path $Path)) { return $true }
        } catch {
            if ($i -lt ($Retries - 1)) {
                Start-Sleep -Milliseconds (400 * ($i + 1))
            }
        }
    }

    return -not (Test-Path $Path)
}

Stop-GradleRelatedProcesses

# 每次构建使用独立输出目录，避免 Windows 上 R.jar 被 IDE 锁住
$env:NYANPASU_OUT_SUFFIX = Get-Date -Format "yyyyMMddHHmmss"
$OutBuild = Join-Path $Root ".out\app-$($env:NYANPASU_OUT_SUFFIX)"
$DefaultOutBuild = Join-Path $Root ".out\app"
$IdeOutBuild = Join-Path $Root ".out\app-ide"
$LegacyBuild = Join-Path $Root "app\build"
Write-Host ">> 构建目录: .out\app-$($env:NYANPASU_OUT_SUFFIX)"

if ($Clean) {
    Write-Host ">> 清理构建目录..."
    foreach ($dir in @($OutBuild, $IdeOutBuild, $DefaultOutBuild, $LegacyBuild)) {
        if (-not (Test-Path $dir)) { continue }
        $ok = Remove-DirWithRetry -Path $dir
        if (-not $ok) {
            $stamp = Get-Date -Format "yyyyMMddHHmmss"
            $stale = "$dir.stale.$stamp"
            Write-Host "!! 无法删除 $dir，重命名为 $stale ..." -ForegroundColor Yellow
            try {
                Rename-Item -LiteralPath $dir -NewName (Split-Path $stale -Leaf) -ErrorAction Stop
            } catch {
                Write-Host "!! 重命名也失败，继续尝试构建..." -ForegroundColor Yellow
            }
        }
    }
}

Write-Host ">> gradlew $GradleTask --no-daemon"
& .\gradlew.bat @($GradleTask.Split(" ") + "--no-daemon")

if ($LASTEXITCODE -ne 0) {
    Write-Host "构建失败。可试: .\scripts\build.ps1 -Clean" -ForegroundColor Red
    exit $LASTEXITCODE
}

$apkGlob = Join-Path $OutBuild "outputs\apk\$ApkSubPath\*.apk"
$apk = Get-ChildItem -Path $apkGlob -ErrorAction SilentlyContinue | Select-Object -First 1
if ($apk) {
    Write-Host "OK: $($apk.FullName)" -ForegroundColor Green
    $latestDir = Join-Path $Root ".out\latest"
    if (Test-Path $latestDir) { Remove-Item -LiteralPath $latestDir -Recurse -Force -ErrorAction SilentlyContinue }
    New-Item -ItemType Directory -Path $latestDir -Force | Out-Null
    $latestName = if ($Release) { "app-release.apk" } else { "app-debug.apk" }
    Copy-Item -LiteralPath $apk.FullName -Destination (Join-Path $latestDir $latestName) -Force
    Write-Host ">> 最新 APK 副本: $latestDir\$latestName" -ForegroundColor Cyan
    if ($Release -and $apk.Name -match "unsigned") {
        Write-Host "!! 未签名：请配置 keystore.properties 后重新构建 Release" -ForegroundColor Yellow
    }
} else {
    Write-Host "构建成功，但未找到 APK: $apkGlob" -ForegroundColor Yellow
}
