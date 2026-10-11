@echo off
cd /d "%~dp0"
if not exist "build" mkdir "build"
javac -d build IrrigationServer.java
if errorlevel 1 (
  echo Java compilation failed.
  pause
  exit /b 1
)
java -cp build IrrigationServer
