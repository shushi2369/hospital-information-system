package com.his.modules.ors.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.ors.entity.OrsOperateRoom;

public interface OrsOperateRoomMapper extends BaseMapper<OrsOperateRoom> {

    /** 行锁读取（一百五十六轮 C-1：同手术间时段并发排台的串行化锚点） */
    @org.apache.ibatis.annotations.Select("SELECT * FROM or_operate_room WHERE id = #{id} FOR UPDATE")
    OrsOperateRoom selectByIdForUpdate(Long id);
}
