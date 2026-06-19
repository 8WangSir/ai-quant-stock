"""技术指标计算模块 - 使用 TA-Lib"""
import numpy as np
import pandas as pd
import talib


def calculate_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """计算 MA/RSI/MACD/成交量均线/52周新高"""
    if df.empty or len(df) < 200:
        return pd.DataFrame()

    close = df["close"].astype(float).values
    volume = df["volume"].astype(float).values
    high = df["high"].astype(float).values

    df = df.copy()
    df["ma20"] = talib.SMA(close, timeperiod=20)
    df["ma50"] = talib.SMA(close, timeperiod=50)
    df["ma200"] = talib.SMA(close, timeperiod=200)
    df["rsi"] = talib.RSI(close, timeperiod=14)
    macd, signal, hist = talib.MACD(close, fastperiod=12, slowperiod=26, signalperiod=9)
    df["macd"] = macd
    df["macd_signal"] = signal
    df["macd_hist"] = hist
    df["vol_ma20"] = talib.SMA(volume, timeperiod=20)
    df["high_52w"] = pd.Series(high).rolling(window=252, min_periods=1).max().values
    return df
