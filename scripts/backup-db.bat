@echo off
REM =====================================================================
REM  MySQL 每日备份（保留 7 天）
REM  用法：scripts\backup-db.bat
REM  依赖：MySQL bin 目录已加入 PATH（或设置 MYSQL_BIN 指向该目录）
REM  凭据：读环境变量 DB_USERNAME / DB_PASSWORD，不写死在脚本里
REM =====================================================================
setlocal enabledelayedexpansion

if "%MYSQL_BIN%"=="" set MYSQL_BIN=mysql
if "%DB_USERNAME%"=="" set DB_USERNAME=root
if "%DB_NAME%"=="" set DB_NAME=bandori_live

set BACKUP_DIR=%~dp0..\backups
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

for /f "tokens=1-4 delims=/ " %%a in ("%date%") do set D=%%a%%b%%c
set T=%time:~0,2%%time:~3,2%%time:~6,2%
set T=%T: =0%
set FILE=%BACKUP_DIR%\%DB_NAME%_%D%_%T%.sql

echo [backup] dumping %DB_NAME% to %FILE%
"%MYSQL_BIN%"dump -u%DB_USERNAME% -p%DB_PASSWORD% --single-transaction --routines --triggers --default-character-set=utf8mb4 %DB_NAME% > "%FILE%"

if errorlevel 1 (
  echo [backup] FAILED
  exit /b 1
)
for %%F in ("%FILE%") do echo [backup] done, size %%~zF bytes

echo [backup] cleaning files older than 7 days
forfiles /p "%BACKUP_DIR%" /m *.sql /d -7 /c "cmd /c del @path" 2>nul
echo [backup] OK
