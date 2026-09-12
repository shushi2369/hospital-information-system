package com.his.modules.clinic.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 就诊详情聚合（C-11，医生工作站主页面数据源）。
 */
@Getter
@Setter
public class VisitDetailResponse {
    private Long id;
    private String visitNo;
    private Long registrationId;
    private PatientInfo patient;
    private String doctorName;
    private String deptName;
    private LocalDate visitDate;
    private String chiefComplaint;
    private String presentIllness;
    private String physicalExam;
    private String advice;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<DiagnosisItem> diagnoses;
    private List<OrderItem> orders;
    private List<PrescriptionDetail> prescriptions;
    private List<ExamItem> examApplications;

    @Getter
    @Setter
    public static class PatientInfo {
        private Long id;
        private String patientNo;
        private String name;
        private Integer gender;
        private LocalDate birthDate;
        private String phone;
        private String allergyHistory;
    }

    @Getter
    @Setter
    public static class DiagnosisItem {
        private Long id;
        private String diagnosisCode;
        private String diagnosisName;
        private Integer diagnosisType;
    }

    @Getter
    @Setter
    public static class OrderItem {
        private Long id;
        private Integer orderType;
        private String content;
        private LocalDateTime createdAt;
    }

    @Getter
    @Setter
    public static class PrescriptionDetail {
        private Long id;
        private String rxNo;
        private BigDecimal totalAmount;
        private Integer status;
        private Integer chargeStatus;
        private String reviewComment;
        private String voidReason;
        private List<PrescriptionItemDetail> items;
    }

    @Getter
    @Setter
    public static class PrescriptionItemDetail {
        private Long id;
        private Long drugId;
        private String drugName;
        private String spec;
        private String dosage;
        private String frequency;
        private String usageRoute;
        private Integer days;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private String usageNote;
    }

    @Getter
    @Setter
    public static class ExamItem {
        private Long id;
        private String applyNo;
        private String itemName;
        private Integer applyType;
        private BigDecimal price;
        private Integer chargeStatus;
        private Integer status;
    }
}
