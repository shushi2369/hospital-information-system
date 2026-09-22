package com.his.modules.cnt.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.cnt.entity.CntRequest;
import com.his.modules.cnt.mapper.CntRequestMapper;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.plt.service.PltService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** CntService 单测：会诊状态机 + 归属校验 + 门诊/住院双段。 */
class CntServiceTest extends UnitTestBase {

    private final CntRequestMapper requestMapper = mock(CntRequestMapper.class);
    private final InpAppService inpAppService = mock(InpAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGen = mock(IdGenerator.class);

    private CntService svc() {
        return new CntService(requestMapper, inpAppService, pltService, idGen);
    }

    private CntRequest request(Long id, int status) {
        var r = new CntRequest();
        r.setId(id); r.setReqNo("HZ" + id); r.setAdmissionId(75L); r.setPatientId(415L);
        r.setStatus(status); r.setReason("测试");
        return r;
    }

    // ---- 申请：住院/门诊至少一项 ----
    @Test
    void create_rejectsNoAdmissionOrVisit() {
        var req = new CntRequest();
        req.setPatientId(415L); req.setReason("无就诊");
        BizException e = assertThrows(BizException.class, () -> svc().create(req));
        assertTrue(e.getMessage().contains("至少关联一项"));
    }

    // ---- 完成：未接受不能完成 ----
    @Test
    void complete_rejectsBeforeAccept() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 10));
        BizException e = assertThrows(BizException.class,
                () -> svc().complete(1L, "会诊意见"));
        assertTrue(e.getMessage().contains("未接受"));
    }

    // ---- 接受：仅待接受状态 ----
    @Test
    void accept_rejectsNonPending() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 30));
        BizException e = assertThrows(BizException.class, () -> svc().accept(1L));
        assertTrue(e.getMessage().contains("待接受"));
    }
}
