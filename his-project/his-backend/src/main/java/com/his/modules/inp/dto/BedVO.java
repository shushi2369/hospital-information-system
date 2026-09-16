package com.his.modules.inp.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 床位视图（床位一览）。 */
@Getter
@Setter
public class BedVO {
    private Long id;
    private Long wardId;
    private String wardName;
    private String bedNo;
    private Integer bedStatus;
    private Long currentAdmissionId;
    private String patientName;
    private BigDecimal bedFee;
}
