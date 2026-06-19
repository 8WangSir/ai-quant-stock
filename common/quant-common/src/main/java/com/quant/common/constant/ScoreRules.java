package com.quant.common.constant;

import java.math.BigDecimal;

public final class ScoreRules {
    public static final BigDecimal ROE_THRESHOLD = new BigDecimal("0.15");
    public static final BigDecimal GROWTH_THRESHOLD = new BigDecimal("0.15");
    public static final BigDecimal DEBT_RATIO_THRESHOLD = new BigDecimal("0.60");
    public static final BigDecimal VOLUME_RATIO_THRESHOLD = new BigDecimal("1.5");
    public static final BigDecimal DRAWDOWN_THRESHOLD = new BigDecimal("0.08");
    public static final int RECOMMEND_MIN_SCORE = 80;
    public static final int RECOMMEND_TOP_N = 10;

    private ScoreRules() {}
}
