package com.his.modules.alert.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.alert.entity.AlertCritical;
import com.his.modules.alert.mapper.AlertCriticalMapper;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.DoctorDTO;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.plt.service.PltService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** AlertService 单测：危急值三段状态机（notify/confirm/close）+ 闭环必填（116 轮 #11）+ 管床校验。 */
class AlertServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AlertCritical.class);
    }

    private final AlertCriticalMapper alertMapper = mock(AlertCriticalMapper.class);
    private final InpAppService inpAppService = mock(InpAppService.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGen = mock(IdGenerator.class);
    private final com.his.modules.system.app.SystemAppService systemAppService =
            mock(com.his.modules.system.app.SystemAppService.class);
    private final com.his.modules.lis.mapper.LisRequestMapper lisRequestMapper =
            mock(com.his.modules.lis.mapper.LisRequestMapper.class);
    private final com.his.modules.ris.mapper.RisRequestMapper risRequestMapper =
            mock(com.his.modules.ris.mapper.RisRequestMapper.class);

    private AlertService svc() {
        return new AlertService(alertMapper, inpAppService, basedataAppService, pltService,
                idGen, systemAppService, lisRequestMapper, risRequestMapper);
    }

    private AlertCritical alert(Long id, int status) {
        AlertCritical a = new AlertCritical();
        a.setId(id);
        a.setAlertNo("WJ-T-" + id);
        a.setStatus(status);
        a.setRequestId(100L);
        return a;
    }

    // ---------------- notify（10 → 20） ----------------

    @Test
    void notify_wrongStatusRejected() {
        when(alertMapper.selectById(1L)).thenReturn(alert(1L, 30));
        BizException e = assertThrows(BizException.class, () -> svc().notify(1L));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void notify_conditionalUpdateAsserted() {
        when(alertMapper.selectById(1L)).thenReturn(alert(1L, 10));
        when(alertMapper.update(any(), any())).thenReturn(0); // 并发失败
        BizException e = assertThrows(BizException.class, () -> svc().notify(1L));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void notify_success() {
        when(alertMapper.selectById(1L)).thenReturn(alert(1L, 10));
        when(alertMapper.update(any(), any())).thenReturn(1);
        assertDoesNotThrow(() -> svc().notify(1L));
    }

    // ---------------- confirm（20 → 30，管床校验） ----------------

    @Test
    void confirm_requiresNotifiedStatus() {
        when(alertMapper.selectById(1L)).thenReturn(alert(1L, 10));
        BizException e = assertThrows(BizException.class, () -> svc().confirm(1L));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void confirm_nonDoctorRejected() {
        setupAs(99L, "NURSE"); // 非医生角色
        when(alertMapper.selectById(1L)).thenReturn(alert(1L, 20));
        when(basedataAppService.getDoctorByUserId(99L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> svc().confirm(1L));
        assertEquals(ErrorCode.A0003, e.getErrorCode());
    }

    @Test
    void confirm_adminBypassesScope() {
        // 基类默认 setupAs(1, ADMIN)
        when(alertMapper.selectById(1L)).thenReturn(alert(1L, 20));
        when(alertMapper.update(any(), any())).thenReturn(1);
        assertDoesNotThrow(() -> svc().confirm(1L));
        verify(basedataAppService, never()).getDoctorByUserId(any());
    }

    // ---------------- close（30 → 40，必填 + 长度） ----------------

    @Test
    void close_blankNoteRejected() {
        BizException e = assertThrows(BizException.class, () -> svc().close(1L, "  "));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void close_nullNoteRejected() {
        BizException e = assertThrows(BizException.class, () -> svc().close(1L, null));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void close_overLengthRejected() {
        String longNote = "a".repeat(257);
        BizException e = assertThrows(BizException.class, () -> svc().close(1L, longNote));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void close_requiresConfirmedStatus() {
        when(alertMapper.selectById(1L)).thenReturn(alert(1L, 20));
        BizException e = assertThrows(BizException.class, () -> svc().close(1L, "已复测"));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void close_successRecordsEvent() {
        when(alertMapper.selectById(1L)).thenReturn(alert(1L, 30));
        when(alertMapper.update(any(), any())).thenReturn(1);
        svc().close(1L, "已复测");
        verify(pltService).recordEvent(eq("alert.closed"), eq("WJ-T-1"), any());
    }
}
