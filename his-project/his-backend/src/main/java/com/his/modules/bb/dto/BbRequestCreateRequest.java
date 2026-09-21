package com.his.modules.bb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** B-03 用血申请创建 */
@Getter
@Setter
public class BbRequestCreateRequest {
    @NotNull(message = "住院不能为空")
    private Long admissionId;
    @NotNull(message = "患者不能为空")
    private Long patientId;
    @NotNull(message = "血型不能为空")
    @Min(1) @Max(4)
    private Integer bloodType;
    @NotNull(message = "Rh 不能为空")
    @Min(1) @Max(2)
    private Integer rh;
    @NotNull(message = "血液成分不能为空")
    @Min(1) @Max(4)
    private Integer component;
    @NotNull(message = "申请量不能为空")
    @Min(50) @Max(5000)
    private Integer volumeMl;
    @Size(max = 256, message = "用血目的过长")
    private String usePurpose;
}
