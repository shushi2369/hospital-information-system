package com.his.modules.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** H-03/H-04 员工建档/更新 */
@Getter
@Setter
public class StaffUpsertRequest {
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名过长")
    private String name;
    @NotNull(message = "科室不能为空")
    private Long deptId;
    @NotBlank(message = "职称不能为空")
    @Size(max = 32, message = "职称过长")
    private String title;
    @Size(max = 64, message = "执业证号过长")
    private String licenseNo;
    @Size(max = 16, message = "联系电话过长")
    private String phone;
    @NotNull(message = "入职日期不能为空")
    private LocalDate entryDate;
    private Long userId;
}
