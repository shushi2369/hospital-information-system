package com.his.modules.ris.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code ris_device} */
@Getter
@Setter
@TableName("ris_device")
public class RisDevice extends BaseEntity {
    private String deviceNo;
    private String deviceName;
    private Integer modality;
    private Integer status;

    @Version
    private Integer version;
}
