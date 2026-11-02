@echo off
REM Common env detection used by start.bat / build.bat.
REM Keep this file ASCII-only: Chinese text in .bat is parsed with the OEM
REM codepage and can break the script on some machines.
REM Sets: JAVA_HOME (JDK 17), MVN (path to mvn.cmd)
setlocal

if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\java.exe" goto :findmvn

set "JAVA_HOME="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if not defined JAVA_HOME if exist "%%D\bin\java.exe" set "JAVA_HOME=%%D"
for /d %%D in ("C:\Program Files\Java\jdk-17*") do if not defined JAVA_HOME if exist "%%D\bin\java.exe" set "JAVA_HOME=%%D"

:findmvn
set "MVN="
for /d %%A in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-*") do for /d %%B in ("%%A\*") do if not defined MVN if exist "%%B\bin\mvn.cmd" set "MVN=%%B\bin\mvn.cmd"
if not defined MVN set "MVN=%~dp0..\backend\mvnw.cmd"

endlocal & set "JAVA_HOME=%JAVA_HOME%" & set "MVN=%MVN%"
exit /b 0
