package com.his.modules.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 退药单（一期整方退药）。 */
@Getter
@Setter
@TableName("phr_return_order")
public class PhrReturnOrder extends BaseEntity {
    private String returnNo;
    private Long dispenseOrderId;
    private Long prescriptionId;
    private Long patientId;
    private String reason;
    private LocalDateTime returnTime;
    private Long operatorId;
    private Integer status;

    @Version
    private Integer version;
}
