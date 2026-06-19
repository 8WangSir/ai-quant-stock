"""综合评分模块 - 四层过滤模型"""
import pandas as pd


ROE_THRESHOLD = 0.15
GROWTH_THRESHOLD = 0.15
DEBT_RATIO_THRESHOLD = 0.60
VOLUME_RATIO_THRESHOLD = 1.5


def score_finance(finance_row: dict) -> int:
    """基本面评分 - 最高40分"""
    score = 0
    if not finance_row:
        return score
    roe = finance_row.get("roe")
    revenue_growth = finance_row.get("revenue_growth")
    profit_growth = finance_row.get("profit_growth")
    debt_ratio = finance_row.get("debt_ratio")
    
    if roe is not None and roe > ROE_THRESHOLD:
        score += 10
    if revenue_growth is not None and revenue_growth > GROWTH_THRESHOLD:
        score += 10
    if profit_growth is not None and profit_growth > GROWTH_THRESHOLD:
        score += 10
    if debt_ratio is not None and debt_ratio < DEBT_RATIO_THRESHOLD:
        score += 10
    return score


def score_trend(indicator_row: dict) -> int:
    """技术趋势评分 - 最高30分"""
    score = 0
    ma20 = indicator_row.get("ma20") or 0
    ma50 = indicator_row.get("ma50") or 0
    ma200 = indicator_row.get("ma200") or 0
    rsi = indicator_row.get("rsi") or 0
    macd_hist = indicator_row.get("macd_hist") or 0
    macd_hist_prev = indicator_row.get("macd_hist_prev") or 0
    close = indicator_row.get("close") or 0
    high_52w = indicator_row.get("high_52w") or 0

    if ma50 > ma200:
        score += 10
    if ma20 > ma50:
        score += 10
    if 50 <= rsi <= 70:
        score += 5
    if macd_hist_prev <= 0 < macd_hist:
        score += 5
    if high_52w > 0 and close >= high_52w * 0.95:
        score += 0  # 接近52周新高作为附加过滤，不计分
    return score


def score_capital(capital_row: dict, indicator_row: dict) -> int:
    """资金面评分 - 最高20分"""
    score = 0
    volume = indicator_row.get("volume") or 0
    vol_ma20 = indicator_row.get("vol_ma20") or 0
    main_inflow = capital_row.get("main_net_inflow") or 0

    if vol_ma20 > 0 and volume > vol_ma20 * VOLUME_RATIO_THRESHOLD:
        score += 10
    if main_inflow > 0:
        score += 10
    return score


def score_industry(industry_score: int) -> int:
    """行业面评分 - 最高10分"""
    return min(industry_score, 10)


def calculate_total_score(
    finance_row: dict,
    indicator_row: dict,
    capital_row: dict,
    industry_score: int,
) -> dict:
    finance = score_finance(finance_row)
    trend = score_trend(indicator_row)
    capital = score_capital(capital_row, indicator_row)
    industry = score_industry(industry_score)
    return {
        "finance_score": finance,
        "trend_score": trend,
        "capital_score": capital,
        "industry_score": industry,
        "total_score": finance + trend + capital + industry,
    }


def filter_recommend_pool(scores_df: pd.DataFrame, min_score: int = 25, top_n: int = 50) -> pd.DataFrame:
    """生成推荐池 - total_score >= 25, 取前50"""
    filtered = scores_df[scores_df["total_score"] >= min_score]
    return filtered.sort_values("total_score", ascending=False).head(top_n)
