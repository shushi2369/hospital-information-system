package com.his.modules.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.basedata.entity.Doctor;
import org.apache.ibatis.annotations.Select;

/**
 * 医生 Mapper（由 HisApplication @MapperScan("com.his.modules.**.mapper") 注册）。
 */
public interface DoctorMapper extends BaseMapper<Doctor> {

    /** 行锁读取（挂号号源校验《05》R2：事务内锁定医生行后计数，防并发超发） */
    @Select("SELECT * FROM bas_doctor WHERE id = #{id} FOR UPDATE")
    Doctor selectByIdForUpdate(Long id);
}
