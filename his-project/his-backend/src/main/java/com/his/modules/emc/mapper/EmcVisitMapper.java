package com.his.modules.emc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.emc.entity.EmcVisit;
import org.apache.ibatis.annotations.Select;

public interface EmcVisitMapper extends BaseMapper<EmcVisit> {

    /** 行锁读取（节点录入与关档互斥，防止关档后仍写入节点） */
    @Select("SELECT * FROM emc_visit WHERE id = #{id} FOR UPDATE")
    EmcVisit selectByIdForUpdate(Long id);
}
