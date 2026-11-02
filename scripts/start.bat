@echo off
REM =====================================================================
REM  Bandori Live —— 一键启动（Windows，无 Docker 方案）
REM  后端 : http://localhost:8080/api/v1   （有 jar 用 java -jar，否则 mvn spring-boot:run）
REM  前端 : http://localhost:5173          （Vite 开发服务器，热更新）
REM  前置：本机 MySQL 8 运行中；backend\.env 已配置
REM
REM  想看「构建产物」效果（无热更新，模拟线上）：start.bat preview
REM =====================================================================
setlocal
cd /d "%~dp0.."

if not exist "backend\.env" (
  echo [警告] 未找到 backend\.env，将使用默认连接（root/空密码），可能导致启动失败
  echo        建议：copy backend\.env.example backend\.env 并填写 DB_PASSWORD
)

echo [1/2] 启动后端（新窗口，自动定位 JDK 17 与 Maven）...
start "bandori-backend" cmd /k "%~dp0_backend.bat"

if /i "%~1"=="preview" (
  echo [2/2] 启动前端构建产物预览（vite preview，4173）...
  start "bandori-frontend" cmd /k "cd /d %~dp0..\frontend && npx vite preview --port 4173 --strictPort"
  echo.
  echo 站点：http://localhost:4173        接口：http://localhost:8080/api/v1
) else (
  echo [2/2] 启动前端开发服务器（Vite dev，5173，热更新）...
  start "bandori-frontend" cmd /k "cd /d %~dp0..\frontend && npm run dev"
  echo.
  echo 站点：http://localhost:5173        接口：http://localhost:8080/api/v1
)

echo 健康检查：http://localhost:8080/api/v1/health
echo 关闭对应窗口即停止服务
endlocal
