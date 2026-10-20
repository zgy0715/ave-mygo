@echo off
REM =====================================================================
REM  Bandori Live —— 一键构建（Windows，无 Docker 方案）
REM  产物：ave-mygo\target\ave-mygo-*.jar + frontend\dist\
REM  前置：JDK 17、Node 18+、MySQL 8 已安装并运行
REM =====================================================================
setlocal
cd /d "%~dp0.."

echo [1/2] 构建后端...
pushd ave-mygo
call mvnw.cmd -q clean package -DskipTests
if errorlevel 1 ( echo 后端构建失败 & popd & exit /b 1 )
popd

echo [2/2] 构建前端...
pushd frontend
call npm ci
if errorlevel 1 ( echo 前端依赖安装失败 & popd & exit /b 1 )
call npm run build
if errorlevel 1 ( echo 前端构建失败 & popd & exit /b 1 )
popd

echo.
echo 构建完成：
dir /b ave-mygo\target\*.jar
echo 前端产物：frontend\dist\
endlocal
