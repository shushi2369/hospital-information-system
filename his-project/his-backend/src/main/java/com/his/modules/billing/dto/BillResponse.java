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
    /** 六十一轮：住院结算专用——押金累计与应退（补）金额（负值=应补），差额收银台线下多退少补 */
    private BigDecimal depositTotal;
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
        /** 已退数量（前端按 剩余可退 = quantity - refundedQty 预填校验，八十七轮契约审计） */
        private BigDecimal refundedQty;
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
