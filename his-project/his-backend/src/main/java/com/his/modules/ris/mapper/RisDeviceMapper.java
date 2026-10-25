package com.his.modules.ris.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.ris.entity.RisDevice;

public interface RisDeviceMapper extends BaseMapper<RisDevice> {

    /** 行锁读取（一百五十六轮 C-1：同设备预约槽位并发占用的串行化锚点） */
    @org.apache.ibatis.annotations.Select("SELECT * FROM ris_device WHERE id = #{id} FOR UPDATE")
    RisDevice selectByIdForUpdate(Long id);
}
