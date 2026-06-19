"""批量因子计算入口"""
import json
import sys
import os
from datetime import date

# 添加当前目录到Python路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import pandas as pd
import redis
from sqlalchemy import create_engine, text

from config import DB_URL, REDIS_URL
from indicators import calculate_indicators
from industry_strength import calculate_industry_strength, rank_industries
from scoring import calculate_total_score, filter_recommend_pool


def get_engine():
    return create_engine(DB_URL)


def calc_indicators(trade_date: str):
    engine = get_engine()
    with engine.connect() as conn:
        codes = conn.execute(text("SELECT DISTINCT code FROM stock_daily")).fetchall()

    for (code,) in codes:
        df = pd.read_sql(
            text("""
                SELECT code, trade_date, open, high, low, close, volume
                FROM stock_daily WHERE code = :code ORDER BY trade_date
            """),
            engine,
            params={"code": code},
        )
        if df.empty:
            continue
        result = calculate_indicators(df)
        if result.empty:
            continue
        latest = result.iloc[-1]
        if str(latest["trade_date"])[:10] != trade_date:
            continue
        with engine.begin() as conn:
            conn.execute(
                text("""
                    INSERT INTO stock_indicator
                    (code, trade_date, ma20, ma50, ma200, rsi, macd, macd_signal, macd_hist, vol_ma20, high_52w)
                    VALUES (:code, :trade_date, :ma20, :ma50, :ma200, :rsi, :macd, :macd_signal, :macd_hist, :vol_ma20, :high_52w)
                    ON CONFLICT (code, trade_date) DO UPDATE SET
                        ma20=EXCLUDED.ma20, ma50=EXCLUDED.ma50, ma200=EXCLUDED.ma200,
                        rsi=EXCLUDED.rsi, macd=EXCLUDED.macd, macd_signal=EXCLUDED.macd_signal,
                        macd_hist=EXCLUDED.macd_hist, vol_ma20=EXCLUDED.vol_ma20, high_52w=EXCLUDED.high_52w
                """),
                {
                    "code": code,
                    "trade_date": trade_date,
                    "ma20": float(latest["ma20"]) if pd.notna(latest["ma20"]) else None,
                    "ma50": float(latest["ma50"]) if pd.notna(latest["ma50"]) else None,
                    "ma200": float(latest["ma200"]) if pd.notna(latest["ma200"]) else None,
                    "rsi": float(latest["rsi"]) if pd.notna(latest["rsi"]) else None,
                    "macd": float(latest["macd"]) if pd.notna(latest["macd"]) else None,
                    "macd_signal": float(latest["macd_signal"]) if pd.notna(latest["macd_signal"]) else None,
                    "macd_hist": float(latest["macd_hist"]) if pd.notna(latest["macd_hist"]) else None,
                    "vol_ma20": float(latest["vol_ma20"]) if pd.notna(latest["vol_ma20"]) else None,
                    "high_52w": float(latest["high_52w"]) if pd.notna(latest["high_52w"]) else None,
                },
            )


def calc_industry_strength(trade_date: str):
    engine = get_engine()
    df = pd.read_sql("SELECT * FROM industry_daily ORDER BY industry_name, trade_date", engine)
    if df.empty:
        return
    strength_df = calculate_industry_strength(df)
    ranked = rank_industries(strength_df, trade_date)
    r = redis.from_url(REDIS_URL)
    rank_list = []
    with engine.begin() as conn:
        for _, row in ranked.iterrows():
            conn.execute(
                text("""
                    INSERT INTO industry_strength
                    (industry_name, trade_date, return_20d, return_60d, return_120d, strength, rank, score)
                    VALUES (:industry_name, :trade_date, :return_20d, :return_60d, :return_120d, :strength, :rank, :score)
                    ON CONFLICT (industry_name, trade_date) DO UPDATE SET
                        return_20d=EXCLUDED.return_20d, return_60d=EXCLUDED.return_60d,
                        return_120d=EXCLUDED.return_120d, strength=EXCLUDED.strength,
                        rank=EXCLUDED.rank, score=EXCLUDED.score
                """),
                {
                    "industry_name": row["industry_name"],
                    "trade_date": trade_date,
                    "return_20d": float(row["return_20d"]) if pd.notna(row["return_20d"]) else 0,
                    "return_60d": float(row["return_60d"]) if pd.notna(row["return_60d"]) else 0,
                    "return_120d": float(row["return_120d"]) if pd.notna(row["return_120d"]) else 0,
                    "strength": float(row["strength"]) if pd.notna(row["strength"]) else 0,
                    "rank": int(row["rank"]),
                    "score": int(row["score"]),
                },
            )
            rank_list.append({
                "industryName": row["industry_name"],
                "tradeDate": trade_date,
                "strength": float(row["strength"]) if pd.notna(row["strength"]) else 0,
                "rank": int(row["rank"]),
                "score": int(row["score"]),
                "return20d": float(row["return_20d"]) if pd.notna(row["return_20d"]) else 0,
                "return60d": float(row["return_60d"]) if pd.notna(row["return_60d"]) else 0,
                "return120d": float(row["return_120d"]) if pd.notna(row["return_120d"]) else 0,
            })
    r.set("industry:rank", json.dumps(rank_list, ensure_ascii=False))


def calc_scores(trade_date: str):
    engine = get_engine()
    stocks = pd.read_sql("SELECT code, industry FROM stock_info", engine)
    scores = []

    for _, stock in stocks.iterrows():
        code = stock["code"]
        industry = stock["industry"]

        finance = pd.read_sql(
            text("SELECT * FROM stock_finance WHERE code = :code ORDER BY report_date DESC LIMIT 1"),
            engine, params={"code": code},
        )
        indicator = pd.read_sql(
            text("SELECT * FROM stock_indicator WHERE code = :code AND trade_date = :date"),
            engine, params={"code": code, "date": trade_date},
        )
        capital = pd.read_sql(
            text("SELECT * FROM stock_capital_flow WHERE code = :code AND trade_date = :date"),
            engine, params={"code": code, "date": trade_date},
        )
        industry_row = pd.read_sql(
            text("SELECT score FROM industry_strength WHERE industry_name = :industry AND trade_date = :date"),
            engine, params={"industry": industry, "date": trade_date},
        )

        finance_dict = finance.iloc[0].to_dict() if not finance.empty else {}
        indicator_dict = indicator.iloc[0].to_dict() if not indicator.empty else {}
        capital_dict = capital.iloc[0].to_dict() if not capital.empty else {}
        industry_score = int(industry_row.iloc[0]["score"]) if not industry_row.empty else 0

        score = calculate_total_score(finance_dict, indicator_dict, capital_dict, industry_score)
        score["code"] = code
        score["trade_date"] = trade_date
        scores.append(score)

    scores_df = pd.DataFrame(scores)
    with engine.begin() as conn:
        for _, row in scores_df.iterrows():
            conn.execute(
                text("""
                    INSERT INTO stock_score
                    (code, trade_date, finance_score, trend_score, capital_score, industry_score, total_score)
                    VALUES (:code, :trade_date, :finance_score, :trend_score, :capital_score, :industry_score, :total_score)
                    ON CONFLICT (code, trade_date) DO UPDATE SET
                        finance_score=EXCLUDED.finance_score, trend_score=EXCLUDED.trend_score,
                        capital_score=EXCLUDED.capital_score, industry_score=EXCLUDED.industry_score,
                        total_score=EXCLUDED.total_score
                """),
                row.to_dict(),
            )
    return scores_df


def generate_recommend_pool(trade_date: str):
    engine = get_engine()
    scores_df = pd.read_sql(
        text("SELECT s.*, i.name FROM stock_score s JOIN stock_info i ON s.code = i.code WHERE s.trade_date = :date"),
        engine, params={"date": trade_date},
    )
    pool = filter_recommend_pool(scores_df)
    r = redis.from_url(REDIS_URL)

    recommend_list = []
    with engine.begin() as conn:
        conn.execute(text("DELETE FROM recommend_pool WHERE trade_date = :date"), {"date": trade_date})
        for rank, (_, row) in enumerate(pool.iterrows(), 1):
            conn.execute(
                text("""
                    INSERT INTO recommend_pool (code, name, trade_date, total_score, rank, signal_type)
                    VALUES (:code, :name, :trade_date, :total_score, :rank, 'BUY')
                """),
                {
                    "code": row["code"],
                    "name": row["name"],
                    "trade_date": trade_date,
                    "total_score": int(row["total_score"]),
                    "rank": rank,
                },
            )
            recommend_list.append({
                "code": row["code"],
                "name": row["name"],
                "total_score": int(row["total_score"]),
                "rank": rank,
            })

    r.set("recommend:today", json.dumps(recommend_list, ensure_ascii=False))
    return recommend_list


def main():
    trade_date = sys.argv[2] if len(sys.argv) > 2 else str(date.today())
    command = sys.argv[1] if len(sys.argv) > 1 else "all"

    handlers = {
        "indicators": lambda: calc_indicators(trade_date),
        "industry": lambda: calc_industry_strength(trade_date),
        "scores": lambda: calc_scores(trade_date),
        "recommend": lambda: generate_recommend_pool(trade_date),
        "all": lambda: (
            calc_indicators(trade_date),
            calc_industry_strength(trade_date),
            calc_scores(trade_date),
            generate_recommend_pool(trade_date),
        ),
    }
    result = handlers.get(command, handlers["all"])()
    print(json.dumps({"status": "ok", "command": command, "trade_date": trade_date}, ensure_ascii=False))


if __name__ == "__main__":
    main()
