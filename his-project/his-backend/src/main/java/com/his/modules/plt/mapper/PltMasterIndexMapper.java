package com.his.modules.plt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.plt.entity.PltMasterIndex;
import org.apache.ibatis.annotations.Select;

public interface PltMasterIndexMapper extends BaseMapper<PltMasterIndex> {

    @Select("SELECT * FROM plt_master_index WHERE patient_id = #{patientId} LIMIT 1")
    PltMasterIndex selectByPatientId(Long patientId);
}
