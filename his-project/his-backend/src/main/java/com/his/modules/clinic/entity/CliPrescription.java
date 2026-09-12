package com.his.modules.clinic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 处方（状态：10待审核 20审核通过 30已发药 40审核驳回 50已作废）。
 */
@Getter
@Setter
@TableName("cli_prescription")
public class CliPrescription extends BaseEntity {
    private String rxNo;
    private Long visitId;
    private Long patientId;
    private Long doctorId;
    private Long deptId;
    private Integer rxType;
    private BigDecimal totalAmount;
    private Integer chargeStatus;
    private Long reviewBy;
    private LocalDateTime reviewAt;
    private String reviewComment;
    private String voidReason;
    private Integer status;

    @Version
    private Integer version;
}
