package com.his.modules.basedata.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * D-07 新增医生请求（绑定登录账号）。
 * phone 字段仅前端展示用：登录账号的联系方式存于 sys_user，bas_doctor 无此列，后端忽略该字段。
 */
@Data
public class DoctorCreateRequest {

    /** 关联登录账号 sys_user.id（唯一，1:1 绑定） */
    @NotNull(message = "登录账号不能为空")
    private Long userId;

    /** 所属科室 bas_department.id（必须是启用的临床科室） */
    @NotNull(message = "所属科室不能为空")
    private Long deptId;

    /** 工号（唯一） */
    @NotBlank(message = "工号不能为空")
    @Size(max = 32)
    private String doctorCode;

    /** 医生姓名 */
    @NotBlank(message = "医生姓名不能为空")
    @Size(max = 64)
    private String doctorName;

    /** 职称：主任医师/副主任医师/主治医师/住院医师 */
    @NotBlank(message = "职称不能为空")
    @Size(max = 32)
    private String title;

    /** 是否专家：1 专家号 0 普通号（不传默认普通号） */
    @Min(value = 0, message = "是否专家取值 1专家号 0普通号")
    @Max(value = 1, message = "是否专家取值 1专家号 0普通号")
    private Integer isExpert;

    /** 普通号挂号费 */
    @NotNull(message = "普通号挂号费不能为空")
    @DecimalMin(value = "0", message = "普通号挂号费不能为负数")
    private BigDecimal normalFee;

    /** 专家号挂号费 */
    @NotNull(message = "专家号挂号费不能为空")
    @DecimalMin(value = "0", message = "专家号挂号费不能为负数")
    private BigDecimal expertFee;

    /** 每日上午/下午各限挂数 */
    @NotNull(message = "每日限挂数不能为空")
    @Min(value = 1, message = "每日限挂数至少为 1")
    private Integer dailyQuota;

    /** 联系电话（仅透传给 sys_user 场景使用，bas_doctor 不存储，后端忽略） */
    @Size(max = 20)
    private String phone;
}
