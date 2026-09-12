package com.his.modules.basedata.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 跨模块输出的医生视图（不含审计字段）。
 */
@Getter
@Setter
public class DoctorDTO {
    private Long id;
    private Long userId;
    private Long deptId;
    private String deptName;
    private String doctorCode;
    private String doctorName;
    private String title;
    private Integer isExpert;
    private BigDecimal normalFee;
    private BigDecimal expertFee;
    private Integer dailyQuota;
    private Integer status;
}
