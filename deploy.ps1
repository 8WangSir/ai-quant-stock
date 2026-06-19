# AI Quant Stock - Docker Deploy Script
$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "AI Quant Stock - Docker Deploy" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Check environment
Write-Host "[1/5] Checking environment..." -ForegroundColor Yellow
try {
    $dockerVersion = docker --version
    Write-Host "Docker: $dockerVersion"
} catch {
    Write-Host "Error: Docker not installed" -ForegroundColor Red
    exit 1
}

try {
    $composeVersion = docker-compose --version
    Write-Host "Docker Compose: $composeVersion"
} catch {
    Write-Host "Error: Docker Compose not installed" -ForegroundColor Red
    exit 1
}

# Build frontend
Write-Host "[2/5] Building frontend..." -ForegroundColor Yellow
Set-Location admin-web
npm install
npm run build
Set-Location ..

# Copy frontend build to admin-service
$staticDir = "services/admin-service/src/main/resources/static"
New-Item -ItemType Directory -Force -Path $staticDir | Out-Null
Copy-Item -Path "admin-web/dist/*" -Destination $staticDir -Recurse -Force

# Build Java services
Write-Host "[3/5] Building Java services..." -ForegroundColor Yellow
mvn clean package -DskipTests

# Build Docker images
Write-Host "[4/5] Building Docker images..." -ForegroundColor Yellow
docker-compose build

# Start services
Write-Host "[5/5] Starting services..." -ForegroundColor Yellow
docker-compose up -d

Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "Deploy complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host "Frontend: http://localhost" -ForegroundColor White
Write-Host "API: http://localhost:8080" -ForegroundColor White
Write-Host "XXL-Job: http://localhost:8088/xxl-job-admin" -ForegroundColor White
Write-Host ""
Write-Host "Logs: docker-compose logs -f [service]" -ForegroundColor Gray
Write-Host "Stop: docker-compose down" -ForegroundColor Gray
Write-Host "=========================================" -ForegroundColor Green
