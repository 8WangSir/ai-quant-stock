package com.quant.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.common.entity.StockDaily;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockDailyMapper extends BaseMapper<StockDaily> {

    @Insert("""
        <script>
        INSERT INTO stock_daily (code, trade_date, open, high, low, close, volume, amount, turnover_rate)
        VALUES
        <foreach collection="list" item="item" separator=",">
            (#{item.code}, #{item.tradeDate}, #{item.open}, #{item.high}, #{item.low},
             #{item.close}, #{item.volume}, #{item.amount}, #{item.turnoverRate})
        </foreach>
        ON CONFLICT (code, trade_date) DO UPDATE SET
            open = EXCLUDED.open,
            high = EXCLUDED.high,
            low = EXCLUDED.low,
            close = EXCLUDED.close,
            volume = EXCLUDED.volume,
            amount = EXCLUDED.amount,
            turnover_rate = EXCLUDED.turnover_rate
        </script>
        """)
    void upsertBatch(@Param("list") List<StockDaily> list);
}
