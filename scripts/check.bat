@echo off
REM Bandori Live —— 一键基线检查入口，实际逻辑在 check.ps1
REM   scripts\check.bat        后端 149 + 前端 83
REM   scripts\check.bat e2e    E2E 21 例（需前后端已启动）
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check.ps1" %*
exit /b %ERRORLEVEL%
