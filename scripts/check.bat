@echo off
REM Bandori Live —— 一键基线检查入口，实际逻辑在 check.ps1
REM   scripts\check.bat        后端测试 + 前端单测（数量见 docs/metrics.md）
REM   scripts\check.bat e2e    E2E（需前后端已启动）
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check.ps1" %*
exit /b %ERRORLEVEL%
