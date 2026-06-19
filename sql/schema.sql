-- AI量化选股系统 - 数据库初始化脚本
-- PostgreSQL + TimescaleDB

CREATE EXTENSION IF NOT EXISTS timescaledb;

-- 1. 股票基础信息表
CREATE TABLE IF NOT EXISTS stock_info (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(16) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    market      VARCHAR(16),
    industry    VARCHAR(64),
    list_date   DATE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_info_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_stock_info_industry ON stock_info (industry);

-- 2. 股票日线表 (TimescaleDB hypertable)
CREATE TABLE IF NOT EXISTS stock_daily (
    code          VARCHAR(16) NOT NULL,
    trade_date    DATE NOT NULL,
    open          DECIMAL(12, 4),
    high          DECIMAL(12, 4),
    low           DECIMAL(12, 4),
    close         DECIMAL(12, 4),
    volume        BIGINT,
    amount        DECIMAL(20, 4),
    turnover_rate DECIMAL(8, 4),
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (code, trade_date)
);

SELECT create_hypertable('stock_capital_flow', 'trade_date', if_not_exists => TRUE);

-- 11. 同步进度表
CREATE TABLE IF NOT EXISTS sync_progress (
    id              BIGSERIAL PRIMARY KEY,
    task_type       VARCHAR(32) NOT NULL,
    current_index   INT DEFAULT 0,
    total_count     INT,
    start_date      DATE,
    end_date        DATE,
    status          VARCHAR(16),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sync_progress_task_type ON sync_progress (task_type);
CREATE INDEX IF NOT EXISTS idx_sync_progress_create_time ON sync_progress (create_time DESC);

CREATE INDEX IF NOT EXISTS idx_stock_daily_date ON stock_daily (trade_date DESC);

-- 3. 财务数据表
CREATE TABLE IF NOT EXISTS stock_finance (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(16) NOT NULL,
    report_date     DATE NOT NULL,
    roe             DECIMAL(8, 4),
    revenue_growth  DECIMAL(8, 4),
    profit_growth   DECIMAL(8, 4),
    debt_ratio      DECIMAL(8, 4),
    cash_flow       DECIMAL(20, 4),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_finance UNIQUE (code, report_date)
);

CREATE INDEX IF NOT EXISTS idx_stock_finance_code ON stock_finance (code);

-- 4. 行业数据表
CREATE TABLE IF NOT EXISTS industry_daily (
    industry_name   VARCHAR(64) NOT NULL,
    trade_date      DATE NOT NULL,
    close           DECIMAL(12, 4),
    change_percent  DECIMAL(8, 4),
    amount          DECIMAL(20, 4),
    capital_flow    DECIMAL(20, 4),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (industry_name, trade_date)
);

SELECT create_hypertable('industry_daily', 'trade_date', if_not_exists => TRUE);

-- 5. 技术指标表
CREATE TABLE IF NOT EXISTS stock_indicator (
    code            VARCHAR(16) NOT NULL,
    trade_date      DATE NOT NULL,
    ma20            DECIMAL(12, 4),
    ma50            DECIMAL(12, 4),
    ma200           DECIMAL(12, 4),
    rsi             DECIMAL(8, 4),
    macd            DECIMAL(12, 6),
    macd_signal     DECIMAL(12, 6),
    macd_hist       DECIMAL(12, 6),
    vol_ma20        DECIMAL(20, 4),
    high_52w        DECIMAL(12, 4),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (code, trade_date)
);

SELECT create_hypertable('stock_indicator', 'trade_date', if_not_exists => TRUE);

-- 6. 行业强度表
CREATE TABLE IF NOT EXISTS industry_strength (
    industry_name   VARCHAR(64) NOT NULL,
    trade_date      DATE NOT NULL,
    return_20d      DECIMAL(8, 4),
    return_60d      DECIMAL(8, 4),
    return_120d     DECIMAL(8, 4),
    strength        DECIMAL(8, 4),
    rank            INT,
    score           INT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (industry_name, trade_date)
);

-- 7. 股票评分表
CREATE TABLE IF NOT EXISTS stock_score (
    code            VARCHAR(16) NOT NULL,
    trade_date      DATE NOT NULL,
    finance_score   INT DEFAULT 0,
    trend_score     INT DEFAULT 0,
    capital_score   INT DEFAULT 0,
    industry_score  INT DEFAULT 0,
    total_score     INT DEFAULT 0,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (code, trade_date)
);

SELECT create_hypertable('stock_score', 'trade_date', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_stock_score_total ON stock_score (trade_date, total_score DESC);

-- 8. 推荐池表
CREATE TABLE IF NOT EXISTS recommend_pool (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(16) NOT NULL,
    name            VARCHAR(64),
    trade_date      DATE NOT NULL,
    total_score     INT,
    rank            INT,
    signal_type     VARCHAR(16) DEFAULT 'BUY',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recommend_pool_date ON recommend_pool (trade_date DESC);

-- 9. 买卖信号表
CREATE TABLE IF NOT EXISTS trade_signal (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(16) NOT NULL,
    trade_date      DATE NOT NULL,
    signal_type     VARCHAR(16) NOT NULL,
    reason          VARCHAR(256),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trade_signal_code_date ON trade_signal (code, trade_date DESC);

-- 10. 资金流数据表
CREATE TABLE IF NOT EXISTS stock_capital_flow (
    code            VARCHAR(16) NOT NULL,
    trade_date      DATE NOT NULL,
    main_net_inflow DECIMAL(20, 4),
    volume_ratio    DECIMAL(8, 4),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (code, trade_date)
);
