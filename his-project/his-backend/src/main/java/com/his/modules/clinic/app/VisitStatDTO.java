package com.his.modules.clinic.app;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 日门诊量统计行（按就诊完成时间）。
 */
@Getter
@Setter
public class VisitStatDTO {
    private LocalDate date;
    private Long count;
}
