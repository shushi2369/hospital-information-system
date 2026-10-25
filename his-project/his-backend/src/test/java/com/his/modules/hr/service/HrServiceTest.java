package com.his.modules.hr.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.hr.dto.TitleChangeRequest;
import com.his.modules.hr.entity.HrStaff;
import com.his.modules.hr.mapper.HrStaffMapper;
import com.his.modules.hr.mapper.HrTitleChangeMapper;
import com.his.modules.plt.service.PltService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** HrService 单测：离职停用 + 职称变更三重门禁（离职拒/同职称拒/未来生效日拒）+ 留痕。 */
class HrServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, HrStaff.class);
    }

    private final HrStaffMapper staffMapper = mock(HrStaffMapper.class);
    private final HrTitleChangeMapper titleChangeMapper = mock(HrTitleChangeMapper.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private HrService svc() {
        return new HrService(staffMapper, titleChangeMapper, pltService, idGenerator);
    }

    private HrStaff staff(int status) {
        HrStaff s = new HrStaff();
        s.setId(1L);
        s.setStaffNo("YG001");
        s.setTitle("护士");
        s.setStatus(status);
        return s;
    }

    @Test
    void create_happy_defaultsStatus1() {
        when(idGenerator.next("YG")).thenReturn("YG001");
        com.his.modules.hr.dto.StaffUpsertRequest req =
                new com.his.modules.hr.dto.StaffUpsertRequest();
        svc().create(req);
        ArgumentCaptor<HrStaff> captor = ArgumentCaptor.forClass(HrStaff.class);
        verify(staffMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
        assertEquals("YG001", captor.getValue().getStaffNo());
    }

    @Test
    void exit_alreadyExited_throws() {
        when(staffMapper.selectById(1L)).thenReturn(staff(0));
        BizException e = assertThrows(BizException.class, () -> svc().exit(1L));
        assertTrue(e.getMessage().contains("已离职"));
    }

    @Test
    void exit_happy_setsStatus0AndExitDate() {
        when(staffMapper.selectById(1L)).thenReturn(staff(1));
        when(staffMapper.updateById(any(HrStaff.class))).thenReturn(1);

        assertDoesNotThrow(() -> svc().exit(1L));

        ArgumentCaptor<HrStaff> captor = ArgumentCaptor.forClass(HrStaff.class);
        verify(staffMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        assertEquals(LocalDate.now(), captor.getValue().getExitDate());
        verify(pltService).recordEvent(eq("hr.staff.exited"), eq("YG001"), anyString());
    }

    @Test
    void exit_concurrentZeroRow_throws() {
        when(staffMapper.selectById(1L)).thenReturn(staff(1));
        when(staffMapper.updateById(any(HrStaff.class))).thenReturn(0);
        BizException e = assertThrows(BizException.class, () -> svc().exit(1L));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void titleChange_exitedStaff_throws() {
        when(staffMapper.selectById(1L)).thenReturn(staff(0));
        TitleChangeRequest req = new TitleChangeRequest();
        req.setNewTitle("主管护师");
        req.setEffectiveDate(LocalDate.now());
        BizException e = assertThrows(BizException.class, () -> svc().titleChange(1L, req));
        assertTrue(e.getMessage().contains("离职"));
    }

    @Test
    void titleChange_sameTitle_throws() {
        when(staffMapper.selectById(1L)).thenReturn(staff(1));
        TitleChangeRequest req = new TitleChangeRequest();
        req.setNewTitle("护士");
        req.setEffectiveDate(LocalDate.now());
        BizException e = assertThrows(BizException.class, () -> svc().titleChange(1L, req));
        assertTrue(e.getMessage().contains("相同"));
    }

    @Test
    void titleChange_futureEffectiveDate_throws() {
        // 即时生效门禁：未来生效日会立刻改掉现职称却把变更记在未来，档案与记录倒挂
        when(staffMapper.selectById(1L)).thenReturn(staff(1));
        TitleChangeRequest req = new TitleChangeRequest();
        req.setNewTitle("主管护师");
        req.setEffectiveDate(LocalDate.now().plusDays(1));
        BizException e = assertThrows(BizException.class, () -> svc().titleChange(1L, req));
        assertTrue(e.getMessage().contains("即时生效"));
    }

    @Test
    void titleChange_happy_writesChangeRecordAndUpdatesStaff() {
        when(staffMapper.selectById(1L)).thenReturn(staff(1));
        when(staffMapper.updateById(any(HrStaff.class))).thenReturn(1);
        TitleChangeRequest req = new TitleChangeRequest();
        req.setNewTitle("主管护师");
        req.setEffectiveDate(LocalDate.now());

        svc().titleChange(1L, req);

        ArgumentCaptor<com.his.modules.hr.entity.HrTitleChange> captor =
                ArgumentCaptor.forClass(com.his.modules.hr.entity.HrTitleChange.class);
        verify(titleChangeMapper).insert(captor.capture());
        assertEquals("护士", captor.getValue().getOldTitle());
        assertEquals("主管护师", captor.getValue().getNewTitle());
        verify(pltService).recordEvent(eq("hr.staff.titleChanged"), eq("YG001"), contains("主管护师"));
    }
}
