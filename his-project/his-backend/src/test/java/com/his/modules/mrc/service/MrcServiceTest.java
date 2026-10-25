package com.his.modules.mrc.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.mrc.dto.BorrowRequest;
import com.his.modules.mrc.entity.MrcBorrow;
import com.his.modules.mrc.entity.MrcRecord;
import com.his.modules.mrc.mapper.MrcBorrowMapper;
import com.his.modules.mrc.mapper.MrcHomepageMapper;
import com.his.modules.mrc.mapper.MrcIcd10Mapper;
import com.his.modules.mrc.mapper.MrcRecordMapper;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.plt.service.PltService;
import com.his.modules.rpt.service.RptService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** MrcService 单测：病案借阅/归还状态机（条件更新防并发悬挂，借阅门禁）。 */
class MrcServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, MrcRecord.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, MrcBorrow.class);
    }

    private final MrcRecordMapper recordMapper = mock(MrcRecordMapper.class);
    private final MrcHomepageMapper homepageMapper = mock(MrcHomepageMapper.class);
    private final MrcBorrowMapper borrowMapper = mock(MrcBorrowMapper.class);
    private final MrcIcd10Mapper icd10Mapper = mock(MrcIcd10Mapper.class);
    private final InpAppService inpAppService = mock(InpAppService.class);
    private final PatientAppService patientAppService = mock(PatientAppService.class);
    private final com.his.modules.system.app.SystemAppService systemAppService =
            mock(com.his.modules.system.app.SystemAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final RptService rptService = mock(RptService.class);

    private MrcService svc() {
        return new MrcService(recordMapper, homepageMapper, borrowMapper, icd10Mapper,
                inpAppService, patientAppService, systemAppService, pltService, rptService);
    }

    private MrcRecord record(int archiveStatus) {
        MrcRecord r = new MrcRecord();
        r.setId(300L);
        r.setAdmissionId(900L);
        r.setArchiveStatus(archiveStatus);
        return r;
    }

    private void stubRecord(int archiveStatus) {
        when(inpAppService.listAdmissionsByStatuses(anyList())).thenReturn(List.of());
        when(recordMapper.selectOne(any())).thenReturn(record(archiveStatus));
    }

    @Test
    void borrow_notArchived_throws() {
        stubRecord(10);
        BizException e = assertThrows(BizException.class,
                () -> svc().borrow(900L, new BorrowRequest()));
        assertEquals(ErrorCode.B6302, e.getErrorCode());
    }

    @Test
    void borrow_alreadyBorrowed_throws() {
        stubRecord(30);
        BizException e = assertThrows(BizException.class,
                () -> svc().borrow(900L, new BorrowRequest()));
        assertEquals(ErrorCode.B6303, e.getErrorCode());
    }

    @Test
    void borrow_happy_insertsBorrowAndFlipsToBorrowed() {
        stubRecord(20);
        when(borrowMapper.insert(any(MrcBorrow.class))).thenReturn(1);
        when(recordMapper.update(isNull(), any())).thenReturn(1);
        BorrowRequest req = new BorrowRequest();
        req.setBorrowerId(77L);
        req.setExpectReturnDays(14);

        svc().borrow(900L, req);

        ArgumentCaptor<MrcBorrow> captor = ArgumentCaptor.forClass(MrcBorrow.class);
        verify(borrowMapper).insert(captor.capture());
        assertEquals(300L, captor.getValue().getMrcId());
        assertEquals(77L, captor.getValue().getBorrowerId());
        assertEquals(1, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getExpectReturnTime());
    }

    @Test
    void borrow_borrowerIdDefaultsToCurrentUser() {
        stubRecord(20);
        when(borrowMapper.insert(any(MrcBorrow.class))).thenReturn(1);
        when(recordMapper.update(isNull(), any())).thenReturn(1);

        svc().borrow(900L, new BorrowRequest());

        ArgumentCaptor<MrcBorrow> captor = ArgumentCaptor.forClass(MrcBorrow.class);
        verify(borrowMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getBorrowerId());
    }

    @Test
    void borrow_concurrentZeroRow_throws_noHangingBorrow() {
        // 条件更新 0 行 = 并发归还/归档抢先：借阅单不允许悬挂在"已借出"
        stubRecord(20);
        when(recordMapper.update(isNull(), any())).thenReturn(0);
        BizException e = assertThrows(BizException.class,
                () -> svc().borrow(900L, new BorrowRequest()));
        assertEquals(ErrorCode.B6303, e.getErrorCode());
    }

    @Test
    void giveBack_noActiveBorrow_throws() {
        stubRecord(30);
        when(borrowMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> svc().giveBack(900L));
        assertEquals(ErrorCode.B6303, e.getErrorCode());
    }

    @Test
    void giveBack_borrowRecordChanged_throws() {
        stubRecord(30);
        when(borrowMapper.selectOne(any())).thenReturn(new MrcBorrow());
        when(borrowMapper.update(isNull(), any())).thenReturn(0);
        BizException e = assertThrows(BizException.class, () -> svc().giveBack(900L));
        assertEquals(ErrorCode.B6303, e.getErrorCode());
    }

    @Test
    void giveBack_happy_returnsBoth() {
        stubRecord(30);
        when(borrowMapper.selectOne(any())).thenReturn(new MrcBorrow());
        when(borrowMapper.update(isNull(), any())).thenReturn(1);
        when(recordMapper.update(isNull(), any())).thenReturn(1);

        assertDoesNotThrow(() -> svc().giveBack(900L));
        // 两次写都必须发生：借阅单 1→2 + 病案 30→20（弱用例修复：删掉任一写不再绿）
        verify(borrowMapper).update(isNull(), any());
        verify(recordMapper).update(isNull(), any());
    }
}
