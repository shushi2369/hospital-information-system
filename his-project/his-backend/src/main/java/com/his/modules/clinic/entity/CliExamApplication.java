package com.his.modules.clinic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 检查/检验申请（一期只到申请+收费，执行/报告为二期）。
 */
@Getter
@Setter
@TableName("cli_exam_application")
public class CliExamApplication extends BaseEntity {
    private String applyNo;
    private Long visitId;
    private Long admissionId;
    private Long patientId;
    private Long doctorId;
    private Long chargeItemId;
    private Integer applyType;
    private String requirement;
    private BigDecimal price;
    private Integer chargeStatus;
    private Integer status;
    /** 联动检查申请id（幂等回填标记） */
    private Long risRequestId;

    @Version
    private Integer version;
}
