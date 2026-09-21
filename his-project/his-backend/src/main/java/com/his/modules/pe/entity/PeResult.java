package com.his.modules.pe.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code pe_result} */
@Getter
@Setter
@TableName("pe_result")
public class PeResult extends BaseEntity {
    private Long recordId;
    private Long chargeItemId;
    private String itemName;
    private String resultValue;
    private Integer abnormalFlag;
    private Long examinerId;
    private String note;

    @Version
    private Integer version;
}
