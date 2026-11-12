# Bandori Live —— 一键基线检查（测试数量见 docs/metrics.md）
# 用法：scripts\check.bat          （或本文件图右键"使用 PowerShell 运行"）
#       scripts\check.bat e2e      （E2E，需 8080 与 5173 已启动）
param([string]$Mode = "unit")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"

function Fail($msg) { Write-Host "  $msg" -ForegroundColor Red; exit 1 }

# 项目路径含中文，mvnw.cmd 无法定位自身；直接调用 ~/.m2 里已下载的 Maven
$mvn = Get-ChildItem -Path "$env:USERPROFILE\.m2\wrapper\dists" -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $mvn) { $mvn = Join-Path $root "backend\mvnw.cmd" }

if ($Mode -eq "e2e") {
    Write-Host "E2E（Playwright，需 8080 与 5173 已启动）..."
    Push-Location (Join-Path $root "frontend")
    & npm run test:e2e
    if ($LASTEXITCODE -ne 0) { Pop-Location; Fail "E2E 失败" }
    Pop-Location
    Write-Host "E2E 通过" -ForegroundColor Green
    exit 0
}

Write-Host "[1/2] 后端测试 ..."
Write-Host "      maven: $($mvn.FullName)"
Push-Location (Join-Path $root "backend")
& $mvn.FullName -q test
if ($LASTEXITCODE -ne 0) { Pop-Location; Fail "后端测试失败" }
Pop-Location

Write-Host "[2/2] 前端单测 ..."
Push-Location (Join-Path $root "frontend")
& npm run test
if ($LASTEXITCODE -ne 0) { Pop-Location; Fail "前端测试失败" }
Pop-Location

Write-Host ""
Write-Host "基线检查通过：后端测试 / 前端单测" -ForegroundColor Green
exit 0
