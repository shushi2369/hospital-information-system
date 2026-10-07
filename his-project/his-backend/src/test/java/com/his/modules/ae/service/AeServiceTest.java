package com.his.modules.ae.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.ae.entity.AeEvent;
import com.his.modules.ae.mapper.AeEventMapper;
import com.his.modules.plt.service.PltService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** AeService 单测：状态机（上报→分派→整改→闭环）+ 类型/严重度校验。 */
class AeServiceTest extends UnitTestBase {

    private final AeEventMapper eventMapper = mock(AeEventMapper.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGen = mock(IdGenerator.class);

    private AeService svc() {
        return new AeService(eventMapper, pltService, idGen, mock(com.his.modules.basedata.app.BasedataAppService.class));
    }

    private AeEvent event(Long id, int status) {
        var e = new AeEvent();
        e.setId(id); e.setEventNo("AE" + id); e.setEventType(1); e.setSeverity(1);
        e.setDepartmentId(1L); e.setDescription("测试");
        e.setStatus(status); e.setReporterId(1L);
        return e;
    }

    // ---- 上报：类型越界拦截 ----
    @Test
    void report_rejectsInvalidType() {
        var e = new AeEvent();
        e.setEventType(9); e.setSeverity(1);
        BizException ex = assertThrows(BizException.class, () -> svc().report(e));
        assertTrue(ex.getMessage().contains("1~7"));
    }

    // ---- 上报：严重度越界拦截 ----
    @Test
    void report_rejectsInvalidSeverity() {
        var e = new AeEvent();
        e.setEventType(1); e.setSeverity(9);
        BizException ex = assertThrows(BizException.class, () -> svc().report(e));
        assertTrue(ex.getMessage().contains("1~4"));
    }

    // ---- 上报：成功（前缀 AE） ----
    @Test
    void report_success() {
        when(idGen.next("AE")).thenReturn("AE001");
        String no = svc().report(validEvent());
        assertTrue(no.startsWith("AE"));
    }

    // ---- 分派：仅已上报状态可分派 ----
    @Test
    void assign_rejectsNonReported() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, 30));
        BizException e = assertThrows(BizException.class, () -> svc().assign(1L));
        assertTrue(e.getMessage().contains("已上报"));
    }

    // ---- 整改：未分派不能整改 ----
    @Test
    void rectify_rejectsBeforeAssign() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, 10));
        BizException e = assertThrows(BizException.class,
                () -> svc().rectify(1L, "整改说明"));
        assertTrue(e.getMessage().contains("未分派"));
    }

    // ---- 闭环：未整改不能闭环 ----
    @Test
    void close_rejectsBeforeRectify() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, 20));
        BizException e = assertThrows(BizException.class, () -> svc().close(1L));
        assertTrue(e.getMessage().contains("未整改"));
    }

    private AeEvent validEvent() {
        var e = new AeEvent();
        e.setEventType(1); e.setSeverity(1);
        e.setDepartmentId(1L); e.setDescription("测试");
        return e;
    }
}
