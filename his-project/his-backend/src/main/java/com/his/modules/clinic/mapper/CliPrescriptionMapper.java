package com.his.modules.clinic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.clinic.entity.CliPrescription;
import org.apache.ibatis.annotations.Select;

public interface CliPrescriptionMapper extends BaseMapper<CliPrescription> {

    /** 行锁读取（发药事务防并发，《03》§4.2） */
    @Select("SELECT * FROM cli_prescription WHERE id = #{id} FOR UPDATE")
    CliPrescription selectByIdForUpdate(Long id);
}
