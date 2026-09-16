package com.his.modules.emr.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** EMR 模板（全院或科室级）。 */
@Getter
@Setter
@TableName("emr_template")
public class EmrTemplate extends BaseEntity {
    private String templateName;
    private Integer docType;
    private Long deptId;
    private String contentJson;
    private Long createdBy;
    private Integer status;
    @Version
    private Integer version;
}
