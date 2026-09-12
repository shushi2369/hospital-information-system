package com.his.modules.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 收费项目 bas_charge_item（《02 数据库设计》§4.5）。
 */
@Getter
@Setter
@TableName("bas_charge_item")
public class ChargeItem extends BaseEntity {

    /** 项目编码（唯一，不可修改） */
    private String itemCode;

    /** 项目名称（如 普通门诊诊查费、血常规、胸部DR） */
    private String itemName;

    /** 类别：1 挂号费 2 诊查费 3 检查费 4 检验费 5 治疗费 6 材料费 7 药品费 */
    private Integer category;

    /** 单价（计费时快照，调价不影响历史单据） */
    private BigDecimal price;

    /** 计价单位，默认 次 */
    private String unit;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
