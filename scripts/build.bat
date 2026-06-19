@echo off
cd /d %~dp0..
echo Building all services...
call mvn clean package -DskipTests
if errorlevel 1 exit /b 1
echo Build completed.
