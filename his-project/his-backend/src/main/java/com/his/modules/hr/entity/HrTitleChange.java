package com.his.modules.hr.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code hr_title_change} */
@Getter
@Setter
@TableName("hr_title_change")
public class HrTitleChange extends BaseEntity {
    private Long staffId;
    private String oldTitle;
    private String newTitle;
    private java.time.LocalDate effectiveDate;
    private Long approverId;
    private String note;

    @Version
    private Integer version;
}
