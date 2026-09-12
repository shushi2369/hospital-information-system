package com.his.modules.clinic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 就诊记录（与挂号单 1:1，接诊时创建；20接诊中 30已完成，完成后全部只读《05》R15）。
 */
@Getter
@Setter
@TableName("cli_visit")
public class CliVisit extends BaseEntity {
    private String visitNo;
    private Long registrationId;
    private Long patientId;
    private Long doctorId;
    private Long deptId;
    private LocalDate visitDate;
    private String chiefComplaint;
    private String presentIllness;
    private String physicalExam;
    private String advice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;

    @Version
    private Integer version;
}
