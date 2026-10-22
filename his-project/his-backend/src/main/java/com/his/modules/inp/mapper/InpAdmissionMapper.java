package com.his.modules.inp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.inp.entity.InpAdmission;
import org.apache.ibatis.annotations.Select;

public interface InpAdmissionMapper extends BaseMapper<InpAdmission> {

    /** 在院或出院未结记录数（防重复住院） */
    @Select("SELECT COUNT(*) FROM inp_admission WHERE patient_id = #{patientId} AND status IN (10, 20)")
    Long countActiveByPatient(Long patientId);

    /** 行锁读取住院主记录：出院与费用录入/押金操作必须串行，防"先查后写"竞态 */
    @Select("SELECT * FROM inp_admission WHERE id = #{id} FOR UPDATE")
    InpAdmission selectByIdForUpdate(Long id);
}
