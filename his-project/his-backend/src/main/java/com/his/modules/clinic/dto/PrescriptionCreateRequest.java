package com.his.modules.clinic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PrescriptionCreateRequest {
    @Valid
    @NotEmpty(message = "处方明细不能为空")
    @jakarta.validation.constraints.Size(max = 50, message = "单张处方明细不能超过 50 行")
    private List<PrescriptionItemRequest> items;
}
