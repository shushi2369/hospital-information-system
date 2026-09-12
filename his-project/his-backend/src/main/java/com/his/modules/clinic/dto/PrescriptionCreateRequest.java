package com.his.modules.clinic.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PrescriptionCreateRequest {
    @NotEmpty(message = "处方明细不能为空")
    private List<PrescriptionItemRequest> items;
}
