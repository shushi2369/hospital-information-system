package com.his.modules.clinic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.clinic.entity.CliExamApplication;
import org.apache.ibatis.annotations.Select;

public interface CliExamApplicationMapper extends BaseMapper<CliExamApplication> {

    /** 行锁读取（退费作废与 RIS 执行状态互斥） */
    @Select("SELECT * FROM cli_exam_application WHERE id = #{id} FOR UPDATE")
    CliExamApplication selectByIdForUpdate(Long id);
}
