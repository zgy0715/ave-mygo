@echo off
REM =====================================================================
REM  Bandori Live —— 一键启动（Windows，无 Docker 方案）
REM  后端 : http://localhost:8080/api/v1   （java -jar，无 jar 时回退 mvnw）
REM  前端 : http://localhost:4173          （vite preview 托管 dist 静态站）
REM  前置：已执行 scripts\build.bat；本机 MySQL 8 运行中；ave-mygo\.env 已配置
REM =====================================================================
setlocal
cd /d "%~dp0.."

if not exist "ave-mygo\.env" (
  echo [警告] 未找到 ave-mygo\.env，将使用默认连接（root/空密码），可能导致启动失败
  echo        建议：copy ave-mygo\.env.example ave-mygo\.env 并填写 DB_PASSWORD
)

set "JAR=ave-mygo\target\ave-mygo-0.0.1-SNAPSHOT.jar"

echo [1/2] 启动后端...
if exist "%JAR%" (
  start "bandori-backend" cmd /k "cd /d %~dp0..\ave-mygo && set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot && java -jar target\ave-mygo-0.0.1-SNAPSHOT.jar"
) else (
  echo       未找到 jar，回退到开发模式（mvnw spring-boot:run）
  start "bandori-backend" cmd /k "cd /d %~dp0..\ave-mygo && set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot && mvnw.cmd spring-boot:run"
)

echo [2/2] 启动前端静态站...
start "bandori-frontend" cmd /k "cd /d %~dp0..\frontend && npx vite preview --port 4173 --strictPort"

echo.
echo 站点：http://localhost:4173        接口：http://localhost:8080/api/v1
echo 健康检查：http://localhost:8080/api/v1/health
echo 关闭对应窗口即停止服务
endlocal
