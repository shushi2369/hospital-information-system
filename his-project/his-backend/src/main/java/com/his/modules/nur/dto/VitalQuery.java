package com.his.modules.nur.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class VitalQuery {
    private Long admissionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
