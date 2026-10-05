package com.his.modules.patient.app;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 跨模块输出的患者视图（工作台需展示年龄与过敏史提示）。
 */
@Getter
@Setter
public class PatientDTO {
    private Long id;
    private String patientNo;
    private String name;
    private Integer gender;
    private LocalDate birthDate;
    private String phone;
    /** 脱敏身份证号（仅 getById 主索引详情等展示出口填充；列表出口不计算） */
    private String maskedIdCardNo;
    private String allergyHistory;
    private Integer status;
}
