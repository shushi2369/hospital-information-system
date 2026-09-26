package com.his.modules.plt.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.patient.entity.PatPatient;
import com.his.modules.patient.mapper.PatPatientMapper;
import com.his.modules.plt.entity.PltMasterIndex;
import com.his.modules.plt.mapper.PltEventLogMapper;
import com.his.modules.plt.mapper.PltIdMapMapper;
import com.his.modules.plt.mapper.PltMasterIndexMapper;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** PltService EMPI 合并单测：自合并守卫（三十五轮）+ 合并标记链守卫 + 正常合并联动停用。 */
class PltServiceTest extends UnitTestBase {

    private final PltMasterIndexMapper masterIndexMapper = mock(PltMasterIndexMapper.class);
    private final PltIdMapMapper idMapMapper = mock(PltIdMapMapper.class);
    private final PltEventLogMapper eventLogMapper = mock(PltEventLogMapper.class);
    private final PatPatientMapper patPatientMapper = mock(PatPatientMapper.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private final PltService service = new PltService(
            masterIndexMapper, idMapMapper, eventLogMapper, patPatientMapper, idGenerator);

    private PltMasterIndex mpi(long id, String mpiNo, Integer mergeFlag) {
        PltMasterIndex m = new PltMasterIndex();
        m.setId(id);
        m.setMpiNo(mpiNo);
        m.setMergeFlag(mergeFlag);
        m.setPatientId(id * 100);
        return m;
    }

    @Test
    void merge_selfRejected() {
        when(masterIndexMapper.selectOne(any())).thenReturn(mpi(1L, "M1", 0));
        BizException e = assertThrows(BizException.class, () -> service.merge("M1", "M1"));
        assertTrue(e.getMessage().contains("不能自合并"));
        verify(masterIndexMapper, never()).updateById(any(PltMasterIndex.class));
        verify(patPatientMapper, never()).updateById(any(PatPatient.class));
    }

    @Test
    void merge_sourceAlreadyMergedRejected() {
        when(masterIndexMapper.selectOne(any()))
                .thenReturn(mpi(1L, "M1", 1), mpi(2L, "M2", 0));
        BizException e = assertThrows(BizException.class, () -> service.merge("M1", "M2"));
        assertTrue(e.getMessage().contains("已合并"));
        verify(masterIndexMapper, never()).updateById(any(PltMasterIndex.class));
    }

    @Test
    void merge_targetMergedRejected() {
        when(masterIndexMapper.selectOne(any()))
                .thenReturn(mpi(1L, "M1", 0), mpi(2L, "M2", 1));
        BizException e = assertThrows(BizException.class, () -> service.merge("M1", "M2"));
        assertTrue(e.getMessage().contains("目标主索引已合并"));
        verify(masterIndexMapper, never()).updateById(any(PltMasterIndex.class));
    }

    @Test
    void merge_happyPathMarksAndDisablesSourcePatient() {
        PltMasterIndex source = mpi(1L, "M1", 0);
        PltMasterIndex target = mpi(2L, "M2", 0);
        when(masterIndexMapper.selectOne(any())).thenReturn(source, target);
        PatPatient sourcePatient = new PatPatient();
        sourcePatient.setId(source.getPatientId());
        sourcePatient.setStatus(1);
        when(patPatientMapper.selectById(source.getPatientId())).thenReturn(sourcePatient);

        String mergedTo = service.merge("M1", "M2");

        assertEquals("M2", mergedTo);
        assertEquals(1, source.getMergeFlag());
        assertEquals(2L, source.getMergedInto());
        assertEquals(0, sourcePatient.getStatus());
        verify(patPatientMapper).updateById(sourcePatient);
        verify(eventLogMapper).insert(any());
    }

    @Test
    void merge_sourcePatientAlreadyDisabledSkipsUpdate() {
        PltMasterIndex source = mpi(1L, "M1", 0);
        when(masterIndexMapper.selectOne(any()))
                .thenReturn(source, mpi(2L, "M2", 0));
        PatPatient disabled = new PatPatient();
        disabled.setId(source.getPatientId());
        disabled.setStatus(0);
        when(patPatientMapper.selectById(source.getPatientId())).thenReturn(disabled);

        service.merge("M1", "M2");

        verify(patPatientMapper, never()).updateById(any(PatPatient.class));
    }
}
