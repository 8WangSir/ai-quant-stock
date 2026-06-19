#!/bin/bash
set -e

echo "========================================="
echo "AI Quant Stock - Docker 部署脚本"
echo "========================================="

# 检查环境
echo "[1/5] 检查环境..."
if ! command -v docker &> /dev/null; then
    echo "错误: Docker 未安装"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "错误: Docker Compose 未安装"
    exit 1
fi

# 构建前端
echo "[2/5] 构建前端..."
cd admin-web
npm install
npm run build
cd ..

# 复制前端构建产物到 admin-service
mkdir -p services/admin-service/src/main/resources/static
cp -r admin-web/dist/* services/admin-service/src/main/resources/static/

# 构建 Java 服务
echo "[3/5] 构建 Java 服务..."
mvn clean package -DskipTests

# 构建并启动 Docker 容器
echo "[4/5] 构建 Docker 镜像..."
docker-compose build

echo "[5/5] 启动服务..."
docker-compose up -d

echo ""
echo "========================================="
echo "部署完成！"
echo "========================================="
echo "前端访问: http://localhost"
echo "API 访问: http://localhost:8080"
echo "XXL-Job: http://localhost:8088/xxl-job-admin"
echo ""
echo "查看日志: docker-compose logs -f [服务名]"
echo "停止服务: docker-compose down"
echo "========================================="
