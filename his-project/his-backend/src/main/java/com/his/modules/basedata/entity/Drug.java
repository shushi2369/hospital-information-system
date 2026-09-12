package com.his.modules.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品 bas_drug（《02 数据库设计》§4.4）。
 */
@Getter
@Setter
@TableName("bas_drug")
public class Drug extends BaseEntity {

    /** 药品编码（唯一，不可修改） */
    private String drugCode;

    /** 药品名称（商品名/名称） */
    private String drugName;

    /** 通用名 */
    private String genericName;

    /** 规格，如 0.25g×24粒 */
    private String spec;

    /** 剂型（胶囊/片剂/颗粒/注射液） */
    private String dosageForm;

    /** 分类：1 西药 2 中成药 3 中药饮片 */
    private Integer category;

    /** 生产厂家 */
    private String manufacturer;

    /** 最小发药单位（盒/瓶/支） */
    private String unit;

    /** 零售价（开方时快照，调价不影响历史单据） */
    private BigDecimal retailPrice;

    /** 库存预警下限 */
    private BigDecimal stockWarningQty;

    /** 是否抗菌药物：1 是 0 否（提示用，不做限制） */
    private Integer isAntibiotic;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
