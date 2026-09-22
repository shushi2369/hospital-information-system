package com.his.modules.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.patient.entity.PatPatient;
import org.apache.ibatis.annotations.Select;

public interface PatPatientMapper extends BaseMapper<PatPatient> {

    @Select("SELECT * FROM pat_patient WHERE id_card_hash = #{idCardHash} LIMIT 1")
    PatPatient selectByIdCardHash(String idCardHash);

    /** 行锁（住院登记串行化用）：同患者并发登记在锁上排队，后到者预检必见在院记录 */
    @Select("SELECT id FROM pat_patient WHERE id = #{id} FOR UPDATE")
    Long lockByIdForUpdate(Long id);
}
