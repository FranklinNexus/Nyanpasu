# 修复 Android Studio 构建时 R.jar 被占用
# 用法: .\scripts\fix-as-build.ps1
# 建议: 先完全关闭 Android Studio，再运行本脚本，然后重新打开 AS → Sync → Run

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

function Remove-DirWithRetry {
    param(
        [string]$Path,
        [int]$Retries = 12
    )

    if (-not (Test-Path $Path)) { return $true }

    for ($i = 0; $i -lt $Retries; $i++) {
        try {
            Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
            if (-not (Test-Path $Path)) { return $true }
        } catch {
            if ($i -lt ($Retries - 1)) {
                Start-Sleep -Milliseconds (500 * ($i + 1))
            }
        }
    }

    return -not (Test-Path $Path)
}

Write-Host ">> 停止 Gradle Daemon..."
& .\gradlew.bat --stop 2>$null | Out-Null
Start-Sleep -Seconds 2

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

Start-Sleep -Seconds 2

$dirs = @(
    (Join-Path $Root ".out\app-ide"),
    (Join-Path $Root ".out\app"),
    (Join-Path $Root "app\build")
)

Write-Host ">> 清理构建目录..."
foreach ($dir in $dirs) {
    if (-not (Test-Path $dir)) { continue }
    $ok = Remove-DirWithRetry -Path $dir
    if ($ok) {
        Write-Host "   已删除: $dir" -ForegroundColor Green
    } else {
        Write-Host "   无法删除（仍被占用）: $dir" -ForegroundColor Yellow
        Write-Host "   请确认 Android Studio 已完全关闭后重试。" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "完成。请重新打开 Android Studio：" -ForegroundColor Cyan
Write-Host "  1. File → Sync Project with Gradle Files"
Write-Host "  2. Build → Rebuild Project"
Write-Host ""
Write-Host "若仍失败，可直接安装: .out\latest\app-debug.apk"
