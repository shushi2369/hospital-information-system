package com.his.modules.clinic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 处方明细（药品名/规格/单价为开方快照）。
 */
@Getter
@Setter
@TableName("cli_prescription_item")
public class CliPrescriptionItem extends BaseEntity {
    private Long prescriptionId;
    private Long drugId;
    private String drugName;
    private String spec;
    private String dosage;
    private String frequency;
    private String usageRoute;
    private Integer days;
    private java.math.BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String usageNote;
    private Integer status;

    @Version
    private Integer version;
}
