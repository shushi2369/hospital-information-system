package com.his.modules.ris.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.ris.entity.RisRequest;
import org.apache.ibatis.annotations.Select;

public interface RisRequestMapper extends BaseMapper<RisRequest> {

    /** 行锁读取（预约/开始/报告状态迁移与退费作废互斥） */
    @Select("SELECT * FROM ris_request WHERE id = #{id} FOR UPDATE")
    RisRequest selectByIdForUpdate(Long id);
}
