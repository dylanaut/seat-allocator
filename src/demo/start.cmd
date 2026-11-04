@echo off
rem Startet die Seat-Allocator-Demo (Windows). Voraussetzung: Java 21 oder neuer.
rem Optionen als Umgebungsvariablen: PORT (Standard 8080), NO_BROWSER=1 (Browser nicht oeffnen), JAVA_HOME.
setlocal
cd /d "%~dp0"

set "JAR=seat-allocator-demo.jar"
if not defined PORT set "PORT=8080"
set "URL=http://localhost:%PORT%/"

set "JAVA_CMD=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"

"%JAVA_CMD%" -version >nul 2>&1
if errorlevel 1 (
  echo Java wurde nicht gefunden. Bitte Java 21 oder neuer installieren ^(z. B. https://adoptium.net^).
  if not defined NO_PAUSE pause
  exit /b 1
)

set "MAJOR="
for /f "tokens=3" %%v in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr /i "version"') do (
  for /f "delims=.-+_" %%m in ("%%~v") do set "MAJOR=%%m"
)
if not defined MAJOR goto :zualt
if %MAJOR% LSS 21 goto :zualt
goto :start

:zualt
echo Java 21 oder neuer erforderlich.
"%JAVA_CMD%" -version
if not defined NO_PAUSE pause
exit /b 1

:start
echo Starte die Seat-Allocator-Demo auf %URL% ^(Beenden mit Strg+C^)

if not defined NO_BROWSER start "" /b cmd /c "ping -n 7 127.0.0.1 >nul & start %URL%"

"%JAVA_CMD%" -Dquarkus.http.port=%PORT% -jar "%JAR%" %*
