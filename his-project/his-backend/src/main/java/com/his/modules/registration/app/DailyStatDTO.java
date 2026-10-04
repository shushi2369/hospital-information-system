package com.his.modules.registration.app;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 日统计行（日期 + 数量）；科室排名复用时携带 name（七十八轮：原 DTO 无 name 字段，
 * MyBatis 静默丢弃 dept_name 别名，科室列自上线起即为空）。
 */
@Getter
@Setter
public class DailyStatDTO {
    private LocalDate date;
    private Long count;
    private String name;
}
