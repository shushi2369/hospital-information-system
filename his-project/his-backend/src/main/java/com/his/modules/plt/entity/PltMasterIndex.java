package com.his.modules.plt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 患者主索引（全院患者唯一身份，一期患者 1:1 映射，多来源预留）。 */
@Getter
@Setter
@TableName("plt_master_index")
public class PltMasterIndex extends BaseEntity {
    private String mpiNo;
    private Long patientId;
    private Integer mergeFlag;
    private Long mergedInto;

    @Version
    private Integer version;
}
