package com.his.modules.system.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class OperationLogQuery extends PageQuery {
    private String username;
    private String module;
    private String bizId;
    private LocalDate startDate;
    private LocalDate endDate;
}
