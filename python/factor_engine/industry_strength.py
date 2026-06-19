"""行业强度计算模块"""
import pandas as pd


def calculate_industry_strength(df: pd.DataFrame) -> pd.DataFrame:
    """
    行业强度 = 20日涨幅 * 0.4 + 60日涨幅 * 0.4 + 120日涨幅 * 0.2
    """
    if df.empty:
        return pd.DataFrame()

    df = df.sort_values(["industry_name", "trade_date"]).copy()
    grouped = df.groupby("industry_name")["close"]

    df["return_20d"] = grouped.pct_change(periods=20)
    df["return_60d"] = grouped.pct_change(periods=60)
    df["return_120d"] = grouped.pct_change(periods=120)

    df["strength"] = (
        df["return_20d"].fillna(0) * 0.4
        + df["return_60d"].fillna(0) * 0.4
        + df["return_120d"].fillna(0) * 0.2
    )
    return df


def rank_industries(df: pd.DataFrame, trade_date: str) -> pd.DataFrame:
    """按行业强度排序并赋分"""
    # 将 trade_date 转换为字符串进行比较（处理 datetime.date 对象）
    df = df.copy()
    df['trade_date_str'] = df['trade_date'].astype(str)
    day_df = df[df['trade_date_str'] == trade_date].copy()
    if day_df.empty:
        return day_df

    day_df = day_df.sort_values("strength", ascending=False).reset_index(drop=True)
    day_df["rank"] = day_df.index + 1
    total = len(day_df)

    def score_for_rank(rank: int) -> int:
        percentile = rank / total
        if percentile <= 0.2:
            return 10
        if percentile <= 0.4:
            return 8
        if percentile <= 0.6:
            return 5
        return 0

    day_df["score"] = day_df["rank"].apply(score_for_rank)
    return day_df
