package com.his.modules.clinic.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 药房队列视图（处方展示，含完整明细）。
 */
@Getter
@Setter
public class RxDisplayDTO {
    private Long id;
    private String rxNo;
    private Long visitId;
    private String visitNo;
    private Long patientId;
    private String patientName;
    private String patientNo;
    private String doctorName;
    private String deptName;
    private BigDecimal totalAmount;
    private Integer status;
    private Integer chargeStatus;
    private LocalDateTime createdAt;
    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private Long itemId;
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
}
