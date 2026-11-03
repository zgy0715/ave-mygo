@echo off
REM Backend launcher, opened in a new window by start.bat.
REM Uses java -jar when the jar exists, otherwise mvn spring-boot:run.
REM ASCII-only on purpose (see env.bat header).
setlocal
call "%~dp0env.bat"

REM Local dev / E2E: relax per-IP rate limits so the Playwright suite
REM (which registers+logs in ~40 times) does not hit 429 mid-run.
REM Production limits stay strict -- cloudstudio.sh does not set these.
set "RATE_LIMIT_AUTH=3000"
set "RATE_LIMIT_ORDER=3000"
set "RATE_LIMIT_ASSISTANT=3000"
set "RATE_LIMIT_DEFAULT=5000"

cd /d "%~dp0..\backend"

echo [backend] dir     = %CD%
echo [backend] JAVA_HOME = %JAVA_HOME%
echo [backend] MVN       = %MVN%

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] JDK 17 not found. Install Temurin 17 or set JAVA_HOME, then retry.
  pause
  exit /b 1
)

if exist "target\ave-mygo-0.0.1-SNAPSHOT.jar" (
  echo [backend] java -jar target\ave-mygo-0.0.1-SNAPSHOT.jar
  "%JAVA_HOME%\bin\java.exe" -jar "target\ave-mygo-0.0.1-SNAPSHOT.jar"
) else (
  echo [backend] no jar found, falling back to: mvn spring-boot:run
  if not exist "%MVN%" (
    echo [ERROR] Maven not found. Run backend\mvnw.cmd -v once, or install Maven.
    pause
    exit /b 1
  )
  call "%MVN%" spring-boot:run
)

echo.
echo [backend] exited with code %ERRORLEVEL%
pause
