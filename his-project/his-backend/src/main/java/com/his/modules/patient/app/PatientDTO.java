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
    private String allergyHistory;
    private Integer status;
}
