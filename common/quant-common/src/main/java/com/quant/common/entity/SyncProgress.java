package com.quant.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sync_progress")
public class SyncProgress {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 同步任务类型
     */
    private String type;

    /**
     * 当前进度
     */
    private Integer current;

    /**
     * 总数量
     */
    private Integer total;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
