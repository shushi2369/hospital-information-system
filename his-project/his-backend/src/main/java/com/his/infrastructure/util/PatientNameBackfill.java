package com.his.infrastructure.util;

import com.his.modules.patient.app.PatientDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 患者姓名批量回填（一百零三轮患者裸列清查）：emc/pe/pub/ris/lis/cnt 六工作台共用。
 * 一次 IN 查询 + 内存映射，勿在循环里单查（N+1）。
 */
public final class PatientNameBackfill {

    public interface PatientIdCarrier {
        Long getPatientId();

        void setPatientName(String name);
    }

    private PatientNameBackfill() {
    }

    public static void fill(List<? extends PatientIdCarrier> rows,
                            com.his.modules.patient.app.PatientAppService patientAppService) {
        Set<Long> ids = new HashSet<>();
        for (PatientIdCarrier row : rows) {
            if (row.getPatientId() != null) ids.add(row.getPatientId());
        }
        if (ids.isEmpty()) return;
        Map<Long, PatientDTO> patients = new HashMap<>();
        for (PatientDTO p : patientAppService.listByIds(new ArrayList<>(ids))) {
            patients.put(p.getId(), p);
        }
        for (PatientIdCarrier row : rows) {
            PatientDTO p = patients.get(row.getPatientId());
            row.setPatientName(p == null ? null : p.getName());
        }
    }
}
