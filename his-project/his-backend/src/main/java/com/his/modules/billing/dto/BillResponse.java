package com.his.modules.billing.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收费单响应/详情（含明细、支付记录、退费记录）。
 */
@Getter
@Setter
public class BillResponse {
    private Long id;
    private String billNo;
    private Long visitId;
    private Long admissionId;
    private String visitNo;
    private Long patientId;
    private String patientName;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
    private BigDecimal paidAmount;
    private BigDecimal refundAmount;
    private Integer payMethod;
    private LocalDateTime payTime;
    private Long cashierId;
    private String cashierName;
    private Integer status;
    private List<Detail> details;
    private List<Payment> payments;
    private List<Refund> refunds;

    @Getter
    @Setter
    public static class Detail {
        private Long id;
        private Integer feeType;
        private Integer sourceType;
        private Long sourceDetailId;
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private Integer refundStatus;
    }

    @Getter
    @Setter
    public static class Payment {
        private Long id;
        private String payNo;
        private Integer payMethod;
        private BigDecimal amount;
        private String transactionId;
        private LocalDateTime payTime;
    }

    @Getter
    @Setter
    public static class Refund {
        private Long id;
        private String refundNo;
        private BigDecimal refundAmount;
        private String reason;
        private LocalDateTime refundTime;
        private String operatorName;
    }
}
