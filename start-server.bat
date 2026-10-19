@echo off
cd /d "%~dp0"
if not exist "build" mkdir "build"
javac -d build IrrigationServer.java
if errorlevel 1 (
  echo Java compilation failed.
  pause
  exit /b 1
)
java -cp "build;lib\mysql-connector-j-26.7.0.jar" IrrigationServer
if errorlevel 1 (
  echo Java backend stopped with an error.
  pause
)
