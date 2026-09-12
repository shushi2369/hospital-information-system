package com.his.modules.clinic.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrescriptionVoidRequest {
    @Size(max = 256, message = "作废原因最长 256 字")
    private String reason;
}
