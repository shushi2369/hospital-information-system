package com.his.modules.basedata.app;

import lombok.Getter;
import lombok.Setter;

/**
 * 跨模块输出的科室视图。
 */
@Getter
@Setter
public class DepartmentDTO {
    private Long id;
    private String deptCode;
    private String deptName;
    private Integer deptType;
    private String location;
    private Integer status;
}
