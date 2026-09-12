package com.his.modules.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.patient.entity.PatPatient;
import org.apache.ibatis.annotations.Select;

public interface PatPatientMapper extends BaseMapper<PatPatient> {

    @Select("SELECT * FROM pat_patient WHERE id_card_hash = #{idCardHash} LIMIT 1")
    PatPatient selectByIdCardHash(String idCardHash);
}
