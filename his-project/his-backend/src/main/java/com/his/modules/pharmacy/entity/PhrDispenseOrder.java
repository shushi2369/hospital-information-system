package com.his.modules.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 发药单（prescription_id 唯一 = 一处方至多一次发药，防重复发药核心约束）。 */
@Getter
@Setter
@TableName("phr_dispense_order")
public class PhrDispenseOrder extends BaseEntity {
    private String dispenseNo;
    private Long prescriptionId;
    private Long patientId;
    private Long dispenserId;
    private BigDecimal totalQuantity;
    private LocalDateTime dispenseTime;
    private Integer status;

    @Version
    private Integer version;
}
