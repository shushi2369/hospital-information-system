package com.his.modules.mrc.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MrcQuery extends PageQuery {
    private String mrcNo;
    private Integer archiveStatus;
    private Integer qcStatus;
}
