package com.his.modules.clinic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 文字医嘱。
 */
@Getter
@Setter
@TableName("cli_medical_order")
public class CliMedicalOrder extends BaseEntity {
    private Long visitId;
    private Integer orderType;
    private String content;
    private Integer status;

    @Version
    private Integer version;
}
