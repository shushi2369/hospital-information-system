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
public class DocOrderExec extends BaseEntity {
    private Long orderId;
    private Long itemId;
    private java.time.LocalDate execDate;
    private String execSlot;
    private Integer execType;
    private Long nurseId;
    private String bedNo;
    private String result;
    private Long chargeDetailId;
    private Integer status;
    @Version
    private Integer version;
}
