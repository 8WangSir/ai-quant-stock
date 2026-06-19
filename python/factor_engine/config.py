"""因子计算配置"""
import os

# 数据库连接配置
DB_URL = os.getenv("DB_URL", "postgresql+psycopg2://quant:quant123@localhost:15432/quant_stock")

# Redis连接配置
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")