@echo off
setlocal
cd /d "%~dp0"
echo Demarrage de MiniPaint Pro V2 (Java 21+)...
java -jar "MiniPaintProV2.jar"
if errorlevel 1 (
  echo.
  echo ERREUR : verifier que Java 21 ou plus est installe.
  echo Dans PowerShell : java -version
  pause
)
