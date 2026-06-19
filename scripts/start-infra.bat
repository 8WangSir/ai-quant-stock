@echo off
echo Starting AI Quant Stock System infrastructure...
cd /d %~dp0..
docker-compose up -d postgres redis
echo Waiting for PostgreSQL...
timeout /t 10 /nobreak > nul
echo Infrastructure started.
echo PostgreSQL: localhost:5432
echo Redis: localhost:6379
