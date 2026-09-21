package com.his.modules.ors.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code or_operate_room} */
@Getter
@Setter
@TableName("or_operate_room")
public class OrsOperateRoom extends BaseEntity {
    private String roomNo;
    private String roomName;
    private Integer status;

    @Version
    private Integer version;
}
