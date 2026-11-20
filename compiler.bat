@echo off
setlocal
cd /d "%~dp0"
if not exist "build\classes" mkdir "build\classes"
if exist "build\sources.txt" del "build\sources.txt"
for /r "src\main\java" %%f in (*.java) do @echo "%%f">>"build\sources.txt"
javac --release 21 -encoding UTF-8 -d "build\classes" @"build\sources.txt"
if errorlevel 1 (
  echo Erreur de compilation.
  pause
  exit /b 1
)
jar --create --file "MiniPaintProV2.jar" --main-class com.minipaint.Main -C "build\classes" .
if errorlevel 1 (
  echo Erreur de construction du JAR.
  pause
  exit /b 1
)
echo Compilation reussie. Le fichier MiniPaintProV2.jar est a jour.
pause
