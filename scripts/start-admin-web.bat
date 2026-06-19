@echo off
cd /d %~dp0..
echo Installing admin-web dependencies...
cd admin-web
call npm install
if errorlevel 1 exit /b 1
echo Starting admin-web dev server on http://localhost:5173
call npm run dev
