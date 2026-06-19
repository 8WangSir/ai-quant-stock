package com.quant.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.common.entity.SyncProgress;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncProgressMapper extends BaseMapper<SyncProgress> {
}
