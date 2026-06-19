package com.quant.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.common.constant.RedisKeys;
import com.quant.common.dto.DashboardStatsDTO;
import com.quant.common.dto.IndustryRankDTO;
import com.quant.common.dto.RecommendStockDTO;
import com.quant.common.dto.StockScoreDTO;
import com.quant.common.dto.TradeSignalDTO;
import com.quant.common.entity.IndustryStrength;
import com.quant.common.entity.RecommendPool;
import com.quant.common.entity.StockScore;
import com.quant.common.entity.TradeSignal;
import com.quant.admin.mapper.IndustryStrengthMapper;
import com.quant.admin.mapper.RecommendPoolMapper;
import com.quant.admin.mapper.StockScoreMapper;
import com.quant.admin.mapper.TradeSignalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminQueryService {

    private final RecommendPoolMapper recommendPoolMapper;
    private final StockScoreMapper stockScoreMapper;
    private final IndustryStrengthMapper industryStrengthMapper;
    private final TradeSignalMapper tradeSignalMapper;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<RecommendStockDTO> getRecommendList(LocalDate tradeDate) {
        if (tradeDate == null) {
            tradeDate = LocalDate.now();
        }

        String cached = redisTemplate.opsForValue().get(RedisKeys.RECOMMEND_TODAY);
        if (cached != null && !cached.isBlank()) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (Exception ignored) {
                // fall through to DB
            }
        }

        // 联表查询：推荐池 + 股票信息 + 最新日线数据 + 前一日收盘价（计算涨跌幅）
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            WITH prev_close AS (
                SELECT code, close AS prev_close
                FROM stock_daily
                WHERE (code, trade_date) IN (
                    SELECT code, MAX(trade_date)
                    FROM stock_daily
                    WHERE trade_date < ?
                    GROUP BY code
                )
            )
            SELECT
                r.code,
                r.name,
                r.trade_date,
                r.total_score,
                r.rank,
                i.industry,
                d.close AS current_price,
                d.close,
                pc.prev_close,
                d.high,
                d.low,
                d.turnover_rate,
                s.finance_score,
                s.trend_score,
                s.capital_score,
                s.industry_score
            FROM recommend_pool r
            LEFT JOIN stock_info i ON r.code = i.code
            LEFT JOIN stock_daily d ON r.code = d.code AND d.trade_date = r.trade_date
            LEFT JOIN prev_close pc ON r.code = pc.code
            LEFT JOIN stock_score s ON r.code = s.code AND s.trade_date = r.trade_date
            WHERE r.trade_date = ?
            ORDER BY r.rank ASC
            LIMIT 50
            """, tradeDate, tradeDate);

        List<RecommendStockDTO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            RecommendStockDTO dto = new RecommendStockDTO();
            dto.setCode((String) row.get("code"));
            dto.setName((String) row.get("name"));
            dto.setTradeDate(tradeDate);
            dto.setTotalScore(((Number) row.get("total_score")).intValue());
            dto.setRank(((Number) row.get("rank")).intValue());
            dto.setIndustry((String) row.get("industry"));

            BigDecimal currentPrice = toBigDecimal(row.get("current_price"));
            BigDecimal prevClose = toBigDecimal(row.get("prev_close"));
            BigDecimal close = toBigDecimal(row.get("close"));

            dto.setCurrentPrice(currentPrice);
            dto.setClose(close);

            // 计算当日涨跌幅
            if (currentPrice != null && prevClose != null && prevClose.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change = currentPrice.subtract(prevClose)
                        .divide(prevClose, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                dto.setChangePercent(change);
            }

            // 计算策略：目标价、持有天数、买卖时间
            calculateStrategy(dto, row);

            result.add(dto);
        }
        return result;
    }

    /**
     * 计算策略参数：目标价、持有天数、买卖时间
     */
    private void calculateStrategy(RecommendStockDTO dto, Map<String, Object> row) {
        BigDecimal currentPrice = dto.getCurrentPrice();
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            dto.setTargetPrice(currentPrice);
            dto.setHoldDays(5);
            dto.setBuyTime("开盘后30分钟内");
            dto.setSellTime("持有期满后卖出");
            dto.setStrategyDesc("数据不足，采用默认策略");
            return;
        }

        Integer totalScore = dto.getTotalScore();
        Integer trendScore = row.get("trend_score") != null ? ((Number) row.get("trend_score")).intValue() : 50;
        Integer financeScore = row.get("finance_score") != null ? ((Number) row.get("finance_score")).intValue() : 50;
        BigDecimal turnoverRate = toBigDecimal(row.get("turnover_rate"));

        // 基于评分和趋势计算目标价涨幅
        BigDecimal targetGainPercent;
        int holdDays;
        String strategyDesc;

        if (totalScore >= 95 && trendScore >= 85) {
            // 超强推荐：高分 + 强趋势
            targetGainPercent = new BigDecimal("15");
            holdDays = 10;
            strategyDesc = "强势突破策略：高评分+强趋势，预期收益15%";
        } else if (totalScore >= 90 && trendScore >= 75) {
            targetGainPercent = new BigDecimal("10");
            holdDays = 7;
            strategyDesc = "趋势跟随策略：高评分+良好趋势，预期收益10%";
        } else if (totalScore >= 85) {
            targetGainPercent = new BigDecimal("8");
            holdDays = 5;
            strategyDesc = "价值成长策略：基本面优秀，预期收益8%";
        } else if (totalScore >= 80) {
            targetGainPercent = new BigDecimal("5");
            holdDays = 3;
            strategyDesc = "短线博弈策略：评分良好，预期收益5%";
        } else {
            targetGainPercent = new BigDecimal("3");
            holdDays = 2;
            strategyDesc = "保守策略：评分一般，小仓位试探";
        }

        // 财务评分加成：财务好则提高目标
        if (financeScore >= 85) {
            targetGainPercent = targetGainPercent.multiply(new BigDecimal("1.2"));
            strategyDesc += "，财务优秀加成20%";
        }

        // 换手率调整：高换手缩短持有期
        if (turnoverRate != null && turnoverRate.compareTo(new BigDecimal("10")) > 0) {
            holdDays = Math.max(1, holdDays - 1);
            strategyDesc += "，高换手缩短持有期";
        }

        BigDecimal targetPrice = currentPrice
                .multiply(BigDecimal.ONE.add(targetGainPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        dto.setTargetPrice(targetPrice);
        dto.setHoldDays(holdDays);
        dto.setStrategyDesc(strategyDesc);

        // 计算买卖时间
        LocalDate buyDate = calculateNextTradeDay(dto.getTradeDate());
        LocalDate sellDate = calculateTradeDayAfter(buyDate, holdDays);

        dto.setBuyTime(buyDate.format(DateTimeFormatter.ofPattern("MM-dd")) + " 开盘后");
        dto.setSellTime(sellDate.format(DateTimeFormatter.ofPattern("MM-dd")) + " 收盘前");
    }

    /**
     * 计算下一个交易日
     */
    private LocalDate calculateNextTradeDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (isWeekend(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * 计算 N 个交易日后的日期
     */
    private LocalDate calculateTradeDayAfter(LocalDate start, int days) {
        LocalDate result = start;
        int count = 0;
        while (count < days) {
            result = result.plusDays(1);
            if (!isWeekend(result)) {
                count++;
            }
        }
        return result;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(obj.toString());
    }

    public StockScoreDTO getStockScore(String code, LocalDate tradeDate) {
        if (tradeDate == null) {
            tradeDate = LocalDate.now();
        }

        StockScore score = stockScoreMapper.selectOne(
                new LambdaQueryWrapper<StockScore>()
                        .eq(StockScore::getCode, code)
                        .eq(StockScore::getTradeDate, tradeDate));

        if (score == null) {
            return null;
        }

        StockScoreDTO dto = new StockScoreDTO();
        dto.setCode(score.getCode());
        dto.setTradeDate(score.getTradeDate());
        dto.setFinanceScore(score.getFinanceScore());
        dto.setTrendScore(score.getTrendScore());
        dto.setCapitalScore(score.getCapitalScore());
        dto.setIndustryScore(score.getIndustryScore());
        dto.setTotalScore(score.getTotalScore());

        List<Map<String, Object>> names = jdbcTemplate.queryForList(
                "SELECT name FROM stock_info WHERE code = ?", code);
        if (!names.isEmpty()) {
            dto.setName((String) names.get(0).get("name"));
        }
        return dto;
    }

    public List<IndustryRankDTO> getIndustryRank(LocalDate tradeDate) {
        if (tradeDate == null) {
            tradeDate = LocalDate.now();
        }

        String cached = redisTemplate.opsForValue().get(RedisKeys.INDUSTRY_RANK);
        if (cached != null && !cached.isBlank()) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (Exception ignored) {
                // fall through to DB
            }
        }

        List<IndustryStrength> list = industryStrengthMapper.selectList(
                new LambdaQueryWrapper<IndustryStrength>()
                        .eq(IndustryStrength::getTradeDate, tradeDate)
                        .orderByAsc(IndustryStrength::getRank));

        List<IndustryRankDTO> result = new ArrayList<>();
        for (IndustryStrength item : list) {
            IndustryRankDTO dto = new IndustryRankDTO();
            dto.setIndustryName(item.getIndustryName());
            dto.setTradeDate(item.getTradeDate());
            dto.setStrength(item.getStrength());
            dto.setRank(item.getRank());
            dto.setScore(item.getScore());
            dto.setReturn20d(item.getReturn20d());
            dto.setReturn60d(item.getReturn60d());
            dto.setReturn120d(item.getReturn120d());
            result.add(dto);
        }
        return result;
    }

    public List<TradeSignalDTO> getSignals(String code, LocalDate tradeDate) {
        List<TradeSignal> signals = tradeSignalMapper.selectList(
                new LambdaQueryWrapper<TradeSignal>()
                        .eq(TradeSignal::getCode, code)
                        .eq(tradeDate != null, TradeSignal::getTradeDate, tradeDate)
                        .orderByDesc(TradeSignal::getTradeDate));

        List<TradeSignalDTO> result = new ArrayList<>();
        for (TradeSignal signal : signals) {
            TradeSignalDTO dto = new TradeSignalDTO();
            dto.setCode(signal.getCode());
            dto.setTradeDate(signal.getTradeDate());
            dto.setSignalType(signal.getSignalType());
            dto.setReason(signal.getReason());
            result.add(dto);
        }
        return result;
    }

    public List<StockScoreDTO> getScoreList(LocalDate tradeDate, Integer minScore) {
        if (tradeDate == null) {
            tradeDate = resolveLatestTradeDate();
        }
        if (minScore == null) {
            minScore = 0;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT s.code, i.name, s.trade_date, s.finance_score, s.trend_score,
                   s.capital_score, s.industry_score, s.total_score
            FROM stock_score s
            LEFT JOIN stock_info i ON s.code = i.code
            WHERE s.trade_date = ? AND s.total_score >= ?
            ORDER BY s.total_score DESC
            LIMIT 200
            """, tradeDate, minScore);

        List<StockScoreDTO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            StockScoreDTO dto = new StockScoreDTO();
            dto.setCode((String) row.get("code"));
            dto.setName((String) row.get("name"));
            dto.setTradeDate(((java.sql.Date) row.get("trade_date")).toLocalDate());
            dto.setFinanceScore(((Number) row.get("finance_score")).intValue());
            dto.setTrendScore(((Number) row.get("trend_score")).intValue());
            dto.setCapitalScore(((Number) row.get("capital_score")).intValue());
            dto.setIndustryScore(((Number) row.get("industry_score")).intValue());
            dto.setTotalScore(((Number) row.get("total_score")).intValue());
            result.add(dto);
        }
        return result;
    }

    public List<TradeSignalDTO> getAllSellSignals(LocalDate tradeDate) {
        if (tradeDate == null) {
            tradeDate = resolveLatestTradeDate();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT ts.code, i.name, ts.trade_date, ts.signal_type, ts.reason
            FROM trade_signal ts
            LEFT JOIN stock_info i ON ts.code = i.code
            WHERE ts.trade_date = ? AND ts.signal_type = 'SELL'
            ORDER BY ts.create_time DESC
            LIMIT 100
            """, tradeDate);

        List<TradeSignalDTO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            TradeSignalDTO dto = new TradeSignalDTO();
            dto.setCode((String) row.get("code"));
            dto.setName((String) row.get("name"));
            dto.setTradeDate(((java.sql.Date) row.get("trade_date")).toLocalDate());
            dto.setSignalType((String) row.get("signal_type"));
            dto.setReason((String) row.get("reason"));
            result.add(dto);
        }
        return result;
    }

    public DashboardStatsDTO getDashboardStats(LocalDate tradeDate) {
        if (tradeDate == null) {
            tradeDate = resolveLatestTradeDate();
        }
        DashboardStatsDTO stats = new DashboardStatsDTO();
        stats.setLatestTradeDate(tradeDate.toString());
        stats.setRecommendCount(countForDate("recommend_pool", tradeDate));
        stats.setScoreCount(countForDate("stock_score", tradeDate));
        stats.setIndustryCount(countForDate("industry_strength", tradeDate));
        stats.setSellSignalCount(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trade_signal WHERE trade_date = ? AND signal_type = 'SELL'",
                Integer.class, tradeDate));
        return stats;
    }

    private Integer countForDate(String table, LocalDate tradeDate) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE trade_date = ?",
                Integer.class, tradeDate);
    }

    private LocalDate resolveLatestTradeDate() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT MAX(trade_date) AS d FROM stock_score");
        if (rows.isEmpty() || rows.get(0).get("d") == null) {
            return LocalDate.now();
        }
        Object d = rows.get(0).get("d");
        if (d instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(d.toString());
    }
}
