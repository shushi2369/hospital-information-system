package com.his.modules.inp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.inp.entity.InpAdmission;
import org.apache.ibatis.annotations.Select;

public interface InpAdmissionMapper extends BaseMapper<InpAdmission> {

    /** 在院或出院未结记录数（防重复住院） */
    @Select("SELECT COUNT(*) FROM inp_admission WHERE patient_id = #{patientId} AND status IN (10, 20)")
    Long countActiveByPatient(Long patientId);
}
