package com.his.modules.emc.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

/** E-07 后补关联就诊/住院（至少一项） */
@Getter
@Setter
public class LinkRequest {
    private Long admissionId;
    private Long visitId;

    @AssertTrue(message = "住院或门诊就诊至少关联一项")
    public boolean hasAny() {
        return admissionId != null || visitId != null;
    }
}
