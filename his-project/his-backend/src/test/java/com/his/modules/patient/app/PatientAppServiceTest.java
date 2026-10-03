package com.his.modules.patient.app;

import com.his.modules.patient.entity.PatPatient;
import com.his.modules.patient.mapper.PatMedicalCardMapper;
import com.his.modules.patient.mapper.PatPatientMapper;
import com.his.modules.plt.entity.PltMasterIndex;
import com.his.modules.plt.mapper.PltMasterIndexMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 六十三轮：EMPI 归一读取——getById 沿合并链解析到存活患者（A→B→C 两跳场景）。
 */
class PatientAppServiceTest {

    private final PatPatientMapper patientMapper = mock(PatPatientMapper.class);
    private final PatMedicalCardMapper cardMapper = mock(PatMedicalCardMapper.class);
    private final PltMasterIndexMapper pltMasterIndexMapper = mock(PltMasterIndexMapper.class);

    private final PatientAppService service = new PatientAppService(
            patientMapper, cardMapper, pltMasterIndexMapper);

    private PltMasterIndex idx(Long id, Long patientId, Integer mergeFlag, Long mergedInto) {
        PltMasterIndex idx = new PltMasterIndex();
        idx.setId(id);
        idx.setPatientId(patientId);
        idx.setMergeFlag(mergeFlag);
        idx.setMergedInto(mergedInto);
        return idx;
    }

    private PatPatient patient(Long id) {
        PatPatient p = new PatPatient();
        p.setId(id);
        p.setName("患者" + id);
        p.setStatus(1);
        return p;
    }

    @Test
    void getById_resolvesTwoHopMergeChain() {
        // A(patient=101, merged→idx2) → B(patient=102, merged→idx3) → C(patient=103, 存活)
        when(pltMasterIndexMapper.selectOne(any()))
                .thenReturn(idx(1L, 101L, 1, 2L))
                .thenReturn(idx(2L, 102L, 1, 3L))
                .thenReturn(idx(3L, 103L, 0, null));
        when(pltMasterIndexMapper.selectById(2L)).thenReturn(idx(2L, 102L, 1, 3L));
        when(pltMasterIndexMapper.selectById(3L)).thenReturn(idx(3L, 103L, 0, null));
        when(patientMapper.selectById(103L)).thenReturn(patient(103L));

        assertEquals(103L, service.getById(101L).getId());
    }

    @Test
    void getById_activePatientUnchanged() {
        when(pltMasterIndexMapper.selectOne(any()))
                .thenReturn(idx(1L, 101L, 0, null));
        when(patientMapper.selectById(101L)).thenReturn(patient(101L));

        assertEquals(101L, service.getById(101L).getId());
        Mockito.verify(patientMapper).selectById(101L);
    }
}
