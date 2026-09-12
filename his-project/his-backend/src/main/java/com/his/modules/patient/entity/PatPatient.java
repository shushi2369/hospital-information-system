package com.his.modules.patient.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 患者档案（idCardNo 为 AES-256-GCM 密文，idCardHash 为 SHA-256 摘要用于唯一校验与检索）。
 */
@Getter
@Setter
@TableName("pat_patient")
public class PatPatient extends BaseEntity {
    private String patientNo;
    private String name;
    private Integer gender;
    private LocalDate birthDate;
    private String idCardNo;
    private String idCardHash;
    private String phone;
    private String address;
    private String allergyHistory;
    private String pastHistory;
    private Integer status;

    @Version
    private Integer version;
}
