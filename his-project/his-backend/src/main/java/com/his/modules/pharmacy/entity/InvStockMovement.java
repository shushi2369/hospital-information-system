package com.his.modules.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 库存流水（append-only）。 */
@Getter
@Setter
@TableName("inv_stock_movement")
public class InvStockMovement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long drugId;
    /** 操作员归属（一百零三轮漂移审计：表列存在实体缺失，691 行全为 0 审计链失效） */
    @com.baomidou.mybatisplus.annotation.TableField(value = "created_by", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long createdBy;
    private Long batchId;
    private Integer movementType;
    private BigDecimal quantity;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    private Integer refType;
    private String refNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
