package com.his.modules.clinic.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 病历暂存（C-03）。
 */
@Getter
@Setter
public class RecordUpdateRequest {
    @Size(max = 512, message = "主诉最长 512 字")
    private String chiefComplaint;
    private String presentIllness;
    @Size(max = 512, message = "体格检查最长 512 字")
    private String physicalExam;
    @Size(max = 512, message = "处理意见最长 512 字")
    private String advice;
}
