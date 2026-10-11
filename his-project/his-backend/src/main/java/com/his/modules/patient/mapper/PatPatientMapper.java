package com.his.modules.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.patient.entity.PatPatient;
import org.apache.ibatis.annotations.Select;

public interface PatPatientMapper extends BaseMapper<PatPatient> {

    @Select("SELECT * FROM pat_patient WHERE id_card_hash = #{idCardHash} LIMIT 1")
    PatPatient selectByIdCardHash(String idCardHash);

    /** 一百一十六轮复核【2】：双候选查询（HMAC 新写入 + SHA-256 存量过渡兼容） */
    @Select("<script>SELECT * FROM pat_patient WHERE id_card_hash IN <foreach item='h' collection='hashes' open='(' separator=',' close=')'>#{h}</foreach> LIMIT 1</script>")
    PatPatient selectByIdCardHashes(@org.apache.ibatis.annotations.Param("hashes") java.util.List<String> hashes);

    /** 行锁（住院登记串行化用）：同患者并发登记在锁上排队，后到者预检必见在院记录 */
    @Select("SELECT id FROM pat_patient WHERE id = #{id} FOR UPDATE")
    Long lockByIdForUpdate(Long id);
}
