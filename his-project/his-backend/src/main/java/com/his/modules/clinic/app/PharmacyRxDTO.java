package com.his.modules.clinic.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 药房视角的处方视图（发药事务内以行锁读取）。
 */
@Getter
@Setter
public class PharmacyRxDTO {
    private Long id;
    private String rxNo;
    private Long patientId;
    private Long visitId;
    private Integer status;
    private Integer chargeStatus;
    private BigDecimal totalAmount;
    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private Long itemId;
        private Long drugId;
        private String drugName;
        private BigDecimal quantity;
    }
}
