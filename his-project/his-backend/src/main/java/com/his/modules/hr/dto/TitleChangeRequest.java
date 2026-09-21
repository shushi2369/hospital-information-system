package com.his.modules.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** H-06 职称变更 */
@Getter
@Setter
public class TitleChangeRequest {
    @NotBlank(message = "新职称不能为空")
    @Size(max = 32, message = "职称过长")
    private String newTitle;
    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;
    @Size(max = 256, message = "备注过长")
    private String note;
}
