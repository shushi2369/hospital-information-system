package com.his.modules.plt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** EMPI 来源映射（多系统接入预留）。 */
@Getter
@Setter
@TableName("plt_id_map")
public class PltIdMap extends BaseEntity {
    private Long mpiId;
    private String sourceSystem;
    private String sourceId;

    @Version
    private Integer version;
}
