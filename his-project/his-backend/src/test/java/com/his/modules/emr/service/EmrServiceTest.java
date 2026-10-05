package com.his.modules.emr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.emr.entity.EmrRecord;
import com.his.modules.emr.mapper.EmrRecordMapper;
import com.his.modules.emr.mapper.EmrTemplateMapper;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.mrc.entity.MrcRecord;
import com.his.modules.mrc.mapper.MrcRecordMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 六十轮：EMR 编辑门禁——归档/借阅病案冻结文书；@Version 乐观锁失败必须显式报错。 */
class EmrServiceTest {

    private final EmrRecordMapper recordMapper = mock(EmrRecordMapper.class);
    private final EmrTemplateMapper templateMapper = mock(EmrTemplateMapper.class);
    private final InpAppService inpAppService = mock(InpAppService.class);
    private final MrcRecordMapper mrcRecordMapper = mock(MrcRecordMapper.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private final EmrService service = new EmrService(
            recordMapper, templateMapper, inpAppService, mrcRecordMapper,
            idGenerator, new ObjectMapper());

    private EmrRecord draft() {
        EmrRecord r = new EmrRecord();
        r.setId(1L);
        r.setAdmissionId(5L);
        r.setDocType(2);
        r.setStatus(10);
        r.setVersion(0);
        return r;
    }

    private MrcRecord mrc(Integer archiveStatus) {
        MrcRecord m = new MrcRecord();
        m.setAdmissionId(5L);
        m.setArchiveStatus(archiveStatus);
        return m;
    }

    @Test
    void update_archivedRecordRejected() {
        when(recordMapper.selectById(1L)).thenReturn(draft());
        when(mrcRecordMapper.selectOne(any())).thenReturn(mrc(20));

        BizException e = assertThrows(() -> service.update(1L, req()));
        assertEquals(ErrorCode.B6202, e.getErrorCode());
    }

    @Test
    void update_borrowedRecordRejected() {
        when(recordMapper.selectById(1L)).thenReturn(draft());
        when(mrcRecordMapper.selectOne(any())).thenReturn(mrc(30));

        assertEquals(ErrorCode.B6202, assertThrows(() -> service.update(1L, req())).getErrorCode());
    }

    @Test
    void update_versionConflictRejected() {
        when(recordMapper.selectById(1L)).thenReturn(draft());
        when(mrcRecordMapper.selectOne(any())).thenReturn(mrc(10));
        when(recordMapper.updateById(any(EmrRecord.class))).thenReturn(0); // 乐观锁失败

        assertEquals(ErrorCode.A0008, assertThrows(() -> service.update(1L, req())).getErrorCode());
    }

    @Test
    void update_writableDraftSucceeds() {
        when(recordMapper.selectById(1L)).thenReturn(draft());
        when(mrcRecordMapper.selectOne(any())).thenReturn(mrc(10));
        when(recordMapper.updateById(any(EmrRecord.class))).thenReturn(1);

        service.update(1L, req()); // 不抛异常即通过
    }

    private BizException assertThrows(Runnable call) {
        try {
            call.run();
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("expected BizException");
    }

    private com.his.modules.emr.dto.EmrRecordUpdateRequest req() {
        com.his.modules.emr.dto.EmrRecordUpdateRequest r = new com.his.modules.emr.dto.EmrRecordUpdateRequest();
        r.setTitle("实验标题");
        return r;
    }
}
