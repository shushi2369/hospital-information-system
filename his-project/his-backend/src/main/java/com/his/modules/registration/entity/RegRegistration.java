package com.his.modules.registration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 挂号单（状态：10已挂号 20已退号 30已就诊 40已过号；号费/诊查费为开单快照）。
 */
@Getter
@Setter
@TableName("reg_registration")
public class RegRegistration extends BaseEntity {
    private String regNo;
    private Long patientId;
    private String cardNo;
    private Long deptId;
    private Long doctorId;
    private LocalDate regDate;
    private Integer period;
    private Integer regType;
    private BigDecimal regFee;
    private BigDecimal consultationFee;
    private Integer queueNo;
    private Integer chargeStatus;
    private Integer status;

    @Version
    private Integer version;
}
