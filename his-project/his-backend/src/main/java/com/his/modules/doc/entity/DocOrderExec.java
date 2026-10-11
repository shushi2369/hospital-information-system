package com.his.modules.doc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 医嘱执行记录（护士核对/执行/皮试，执行即计费）。 */
@Getter
@Setter
@TableName("doc_order_exec")
public class DocOrderExec extends BaseEntity implements com.his.infrastructure.util.PatientNameBackfill.PatientIdCarrier {
    private Long orderId;
    private Long itemId;
    private java.time.LocalDate execDate;
    private String execSlot;
    private Integer execType;
    private Long nurseId;
    /** 展示字段（不入库）：详情批量回填（一百零二轮裸 ID 清查 #2 幽灵列） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String nurseName;
    private String bedNo;
    private String result;
    private Long chargeDetailId;
    private Integer status;
    /** 展示字段（不入库）：一百一十二轮 B03 护理待执行单回填——防"床号重复时盲执行到错误患者" */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Long patientId;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String patientName;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String itemName;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String dosage;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String frequency;
    @Version
    private Integer version;
}
