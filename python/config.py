"""全局配置"""
import os
from dotenv import load_dotenv

load_dotenv()

DB_URL = os.getenv(
    "DATABASE_URL",
    "postgresql+psycopg2://quant:quant123@localhost:15432/quant_stock",
)
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")
TICKFLOW_BASE_URL = os.getenv("TICKFLOW_BASE_URL", "https://api.tickflow.org")
TICKFLOW_API_KEY = os.getenv("TICKFLOW_API_KEY", "")
