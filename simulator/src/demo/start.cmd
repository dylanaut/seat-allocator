@echo off
rem Startet die Seat-Allocator-Demo (Windows). Voraussetzung: Java 21 oder neuer.
rem Optionen als Umgebungsvariablen: PORT (Standard 8080), NO_BROWSER=1 (Browser nicht oeffnen), JAVA_HOME, JAVA_OPTS (JVM-Optionen).
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
set "JV_DATEI=%TEMP%\seat-allocator-java-version.txt"
"%JAVA_CMD%" -version > "%JV_DATEI%" 2>&1
for /f "tokens=3" %%v in ('findstr /i "version" "%JV_DATEI%"') do (
  for /f "delims=.-+_" %%m in ("%%~v") do set "MAJOR=%%m"
)
del "%JV_DATEI%" >nul 2>&1
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

set "LAYOUTS="
if not defined WAGENLAYOUTS_VERZEICHNIS if exist "%~dp0layouts\" set "LAYOUTS=-Dwagenlayouts.verzeichnis=%~dp0layouts"

"%JAVA_CMD%" -Dquarkus.http.port=%PORT% %LAYOUTS% %JAVA_OPTS% -jar "%JAR%" %*
