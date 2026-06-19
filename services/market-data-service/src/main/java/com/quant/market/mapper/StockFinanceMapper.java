package com.quant.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.common.entity.StockFinance;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockFinanceMapper extends BaseMapper<StockFinance> {

    @Insert("""
        <script>
        INSERT INTO stock_finance (code, report_date, roe, revenue_growth, profit_growth, debt_ratio, cash_flow)
        VALUES
        <foreach collection="list" item="item" separator=",">
            (#{item.code}, #{item.reportDate}, #{item.roe}, #{item.revenueGrowth},
             #{item.profitGrowth}, #{item.debtRatio}, #{item.cashFlow})
        </foreach>
        ON CONFLICT (code, report_date) DO UPDATE SET
            roe = EXCLUDED.roe,
            revenue_growth = EXCLUDED.revenue_growth,
            profit_growth = EXCLUDED.profit_growth,
            debt_ratio = EXCLUDED.debt_ratio,
            cash_flow = EXCLUDED.cash_flow
        </script>
        """)
    void upsertBatch(@Param("list") List<StockFinance> list);
}
