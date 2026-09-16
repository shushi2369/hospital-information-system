package com.his.modules.mrc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BorrowRequest {
    private Long borrowerId;
    @Min(value = 1, message = "预计归还天数至少 1 天")
    @Max(value = 90, message = "预计归还天数最长 90 天")
    private Integer expectReturnDays;
}
