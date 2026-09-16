package com.his.modules.inp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.inp.entity.InpBed;

public interface InpBedMapper extends BaseMapper<InpBed> {

    /** 分配床位（条件更新防一床两占，影响行数=0 即 B6001） */
    @org.apache.ibatis.annotations.Update("UPDATE inp_bed SET bed_status = 2, current_admission_id = #{admissionId} "
            + "WHERE id = #{bedId} AND bed_status = 1")
    int occupyBed(Long bedId, Long admissionId);

    /** 释放床位 */
    @org.apache.ibatis.annotations.Update("UPDATE inp_bed SET bed_status = 1, current_admission_id = NULL "
            + "WHERE id = #{bedId} AND bed_status = 2 AND current_admission_id = #{admissionId}")
    int releaseBed(Long bedId, Long admissionId);
}
