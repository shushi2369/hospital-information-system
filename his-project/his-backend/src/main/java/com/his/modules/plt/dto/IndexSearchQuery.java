package com.his.modules.plt.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IndexSearchQuery extends PageQuery {
    private String name;
    private String mpiNo;
    private String patientNo;
    private String phone;
}
