package com.his.modules.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 处方审核记录（留痕）。 */
@Getter
@Setter
@TableName("phr_review_record")
public class PhrReviewRecord extends BaseEntity {
    private Long prescriptionId;
    private Long reviewerId;
    private Integer reviewAction;
    private String comment;
    /** 1 有效（一百零三轮漂移审计：作废/失效审核记录无法在实体层过滤） */
    private Integer status;

    @Version
    private Integer version;
}
