package com.quant.strategy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.constant.ScoreRules;
import com.quant.common.dto.RecommendStockDTO;
import com.quant.common.dto.TradeSignalDTO;
import com.quant.common.entity.RecommendPool;
import com.quant.common.entity.TradeSignal;
import com.quant.strategy.mapper.RecommendPoolMapper;
import com.quant.strategy.mapper.TradeSignalMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyService {

    private final RecommendPoolMapper recommendPoolMapper;
    private final TradeSignalMapper tradeSignalMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PythonRunner pythonRunner;

    @Transactional
    public void generateRecommendPool(LocalDate tradeDate) {
        pythonRunner.run("recommend", tradeDate);
        log.info("Recommend pool generated for {}", tradeDate);
    }

    public List<RecommendStockDTO> getRecommendList(LocalDate tradeDate) {
        List<RecommendPool> pools = recommendPoolMapper.selectList(
                new LambdaQueryWrapper<RecommendPool>()
                        .eq(RecommendPool::getTradeDate, tradeDate)
                        .orderByAsc(RecommendPool::getRank));

        List<RecommendStockDTO> result = new ArrayList<>();
        for (RecommendPool pool : pools) {
            RecommendStockDTO dto = new RecommendStockDTO();
            dto.setCode(pool.getCode());
            dto.setName(pool.getName());
            dto.setTradeDate(pool.getTradeDate());
            dto.setTotalScore(pool.getTotalScore());
            dto.setRank(pool.getRank());
            result.add(dto);
        }
        return result;
    }

    @Transactional
    public void detectSellSignals(LocalDate tradeDate) {
        String sql = """
            SELECT s.code, i.name, d.close, ind.ma50, ind.macd_hist,
                   cf.main_net_inflow, ist.score as industry_score
            FROM stock_score s
            JOIN stock_info i ON s.code = i.code
            JOIN stock_daily d ON s.code = d.code AND s.trade_date = d.trade_date
            LEFT JOIN stock_indicator ind ON s.code = ind.code AND s.trade_date = ind.trade_date
            LEFT JOIN stock_capital_flow cf ON s.code = cf.code AND s.trade_date = cf.trade_date
            LEFT JOIN industry_strength ist ON i.industry = ist.industry_name AND s.trade_date = ist.trade_date
            WHERE s.trade_date = ?
            """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tradeDate);

        for (Map<String, Object> row : rows) {
            String code = (String) row.get("code");
            List<String> reasons = new ArrayList<>();

            BigDecimal close = toDecimal(row.get("close"));
            BigDecimal ma50 = toDecimal(row.get("ma50"));
            if (close != null && ma50 != null && close.compareTo(ma50) < 0) {
                reasons.add("跌破MA50");
            }

            BigDecimal macdHist = toDecimal(row.get("macd_hist"));
            if (macdHist != null && macdHist.compareTo(BigDecimal.ZERO) < 0) {
                reasons.add("MACD死叉");
            }

            Integer industryScore = row.get("industry_score") != null
                    ? ((Number) row.get("industry_score")).intValue() : null;
            if (industryScore != null && industryScore <= 0) {
                reasons.add("行业强度下降");
            }

            BigDecimal mainInflow = toDecimal(row.get("main_net_inflow"));
            if (mainInflow != null && mainInflow.compareTo(BigDecimal.ZERO) < 0) {
                reasons.add("主力资金流出");
            }

            BigDecimal drawdown = calculateDrawdown(code, tradeDate);
            if (drawdown != null && drawdown.compareTo(ScoreRules.DRAWDOWN_THRESHOLD) > 0) {
                reasons.add("回撤超过8%");
            }

            if (!reasons.isEmpty()) {
                TradeSignal signal = new TradeSignal();
                signal.setCode(code);
                signal.setTradeDate(tradeDate);
                signal.setSignalType("SELL");
                signal.setReason(String.join("; ", reasons));
                tradeSignalMapper.insert(signal);
            }
        }
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

    private BigDecimal calculateDrawdown(String code, LocalDate tradeDate) {
        String sql = """
            SELECT close FROM stock_daily
            WHERE code = ? AND trade_date <= ?
            ORDER BY trade_date DESC LIMIT 60
            """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, code, tradeDate);
        if (rows.size() < 2) {
            return null;
        }
        BigDecimal latest = toDecimal(rows.get(0).get("close"));
        BigDecimal peak = latest;
        for (Map<String, Object> row : rows) {
            BigDecimal price = toDecimal(row.get("close"));
            if (price.compareTo(peak) > 0) {
                peak = price;
            }
        }
        if (peak.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return peak.subtract(latest).divide(peak, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
