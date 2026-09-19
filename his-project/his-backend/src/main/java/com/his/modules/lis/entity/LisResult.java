package com.his.modules.lis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code lis_result} */
@Getter
@Setter
@TableName("lis_result")
public class LisResult extends BaseEntity {
    private Long requestId;
    private String itemName;
    private String resultValue;
    private String unit;
    private String referenceRange;
    private Integer abnormalFlag;
    private Integer criticalFlag;
    private String instrument;
    private Integer status;

    @Version
    private Integer version;
}
