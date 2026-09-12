package com.his.modules.registration.app;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 日统计行（日期 + 数量）。
 */
@Getter
@Setter
public class DailyStatDTO {
    private LocalDate date;
    private Long count;
}
