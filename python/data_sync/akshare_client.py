"""AKShare 容灾数据源客户端"""
import json
import sys
import time
import warnings
from datetime import datetime

# 抑制第三方库的 warning 输出
warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", category=DeprecationWarning)

import akshare as ak
import pandas as pd

MAX_RETRIES = 2
RETRY_DELAY = 3  # seconds


def _retry(func):
    """带重试的装饰器"""
    def wrapper(*args, **kwargs):
        for attempt in range(MAX_RETRIES + 1):
            try:
                return func(*args, **kwargs)
            except Exception as e:
                if attempt < MAX_RETRIES:
                    time.sleep(RETRY_DELAY)
                else:
                    raise
        return []
    return wrapper


@_retry
def fetch_stock_list():
    df = ak.stock_info_a_code_name()
    result = []
    for _, row in df.iterrows():
        result.append({
            "code": str(row["code"]),
            "name": str(row["name"]),
            "market": "A",
            "industry": None,
        })
    return result


@_retry
def fetch_daily(code: str, start: str, end: str):
    try:
        df = ak.stock_zh_a_hist(
            symbol=code,
            period="daily",
            start_date=start.replace("-", ""),
            end_date=end.replace("-", ""),
            adjust="qfq",
        )
        if df is None or df.empty:
            return []
        result = []
        for _, row in df.iterrows():
            result.append({
                "trade_date": pd.to_datetime(row["日期"]).strftime("%Y-%m-%d"),
                "open": float(row["开盘"]),
                "high": float(row["最高"]),
                "low": float(row["最低"]),
                "close": float(row["收盘"]),
                "volume": int(row["成交量"]),
                "amount": float(row["成交额"]),
                "turnover_rate": float(row.get("换手率", 0) or 0),
            })
        return result
    except Exception as e:
        print(f"AKShare fetch_daily error for {code}: {e}", file=sys.stderr)
        return []


@_retry
def fetch_finance(code: str):
    try:
        df = ak.stock_financial_analysis_indicator(symbol=code)
        if df.empty:
            return []
        latest = df.iloc[0]
        report_date = pd.to_datetime(latest.get("日期", datetime.now())).strftime("%Y-%m-%d")
        return [{
            "report_date": report_date,
            "roe": _safe_float(latest.get("净资产收益率(%)", 0)) / 100,
            "revenue_growth": _safe_float(latest.get("主营业务收入增长率(%)", 0)) / 100,
            "profit_growth": _safe_float(latest.get("净利润增长率(%)", 0)) / 100,
            "debt_ratio": _safe_float(latest.get("资产负债率(%)", 0)) / 100,
            "cash_flow": _safe_float(latest.get("经营活动产生的现金流量净额", 0)),
        }]
    except Exception:
        return []


@_retry
def fetch_industry(trade_date: str):
    try:
        df = ak.stock_board_industry_name_em()
        if df is None or df.empty:
            return []
        result = []
        for _, row in df.iterrows():
            result.append({
                "industry_name": str(row["板块名称"]),
                "trade_date": trade_date,
                "close": _safe_float(row.get("最新价", 0)),
                "change_percent": _safe_float(row.get("涨跌幅", 0)) / 100,
                "amount": _safe_float(row.get("总成交额", 0)),
                "capital_flow": _safe_float(row.get("主力净流入", 0)),
            })
        return result
    except Exception as e:
        print(f"AKShare fetch_industry error: {e}", file=sys.stderr)
        return []


@_retry
def fetch_capital_flow(trade_date: str):
    try:
        df = ak.stock_individual_fund_flow_rank(indicator="今日")
        if df is None or df.empty:
            return []
        result = []
        for _, row in df.iterrows():
            result.append({
                "code": str(row["代码"]),
                "trade_date": trade_date,
                "main_net_inflow": _safe_float(row.get("主力净流入-净额", 0)),
                "volume_ratio": _safe_float(row.get("量比", 0)),
            })
        return result
    except Exception as e:
        print(f"AKShare fetch_capital_flow error: {e}", file=sys.stderr)
        return []


def _safe_float(value):
    try:
        if pd.isna(value):
            return 0.0
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def main():
    if len(sys.argv) < 2:
        print(json.dumps([]))
        return

    command = sys.argv[1]
    handlers = {
        "stock_list": lambda: fetch_stock_list(),
        "daily": lambda: fetch_daily(sys.argv[2], sys.argv[3], sys.argv[4]),
        "finance": lambda: fetch_finance(sys.argv[2]),
        "industry": lambda: fetch_industry(sys.argv[2]),
        "capital_flow": lambda: fetch_capital_flow(sys.argv[2]),
    }
    handler = handlers.get(command)
    if handler is None:
        print(json.dumps([]))
        return
    print(json.dumps(handler(), ensure_ascii=False))


if __name__ == "__main__":
    main()
