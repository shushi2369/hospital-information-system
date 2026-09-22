package com.his.modules.ors.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.ors.dto.SurgeryCreateRequest;
import com.his.modules.ors.entity.OrsSurgeryRequest;
import com.his.modules.ors.mapper.*;
import com.his.modules.plt.service.PltService;
import com.his.modules.system.app.SystemAppService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** OrsService 状态机 + 安全门禁 + 归属校验单测。 */
class OrsServiceTest extends UnitTestBase {

    private final OrsSurgeryRequestMapper requestMapper = mock(OrsSurgeryRequestMapper.class);
    private final OrsScheduleMapper scheduleMapper = mock(OrsScheduleMapper.class);
    private final OrsCheckRecordMapper checkMapper = mock(OrsCheckRecordMapper.class);
    private final OrsAnesthesiaRecordMapper anesthesiaMapper = mock(OrsAnesthesiaRecordMapper.class);
    private final OrsPostopRecordMapper postopMapper = mock(OrsPostopRecordMapper.class);
    private final OrsOperateRoomMapper roomMapper = mock(OrsOperateRoomMapper.class);
    private final InpAppService inpAppService = mock(InpAppService.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final SystemAppService systemAppService = mock(SystemAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGen = mock(IdGenerator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrsService svc() {
        return new OrsService(requestMapper, scheduleMapper, checkMapper,
                anesthesiaMapper, postopMapper, roomMapper,
                inpAppService, basedataAppService, systemAppService,
                pltService, idGen, objectMapper);
    }

    private OrsSurgeryRequest request(Long id, int status) {
        var r = new OrsSurgeryRequest();
        r.setId(id); r.setRequestNo("SS" + id); r.setAdmissionId(75L); r.setPatientId(415L);
        r.setSurgeryName("阑尾切除术"); r.setDiagnosis("急性阑尾炎");
        r.setPlannedDate(LocalDate.now()); r.setAnesthesiaMethod(1);
        r.setStatus(status); r.setChargeStatus(0);
        return r;
    }

    // ---- 归属校验：跨患者拦截 ----
    @Test
    void create_rejectsCrossPatient() {
        var adm = new InpAdmission();
        adm.setId(75L); adm.setPatientId(415L); adm.setStatus(10);
        when(inpAppService.requireInHospital(75L)).thenReturn(adm);
        var req = new SurgeryCreateRequest();
        req.setAdmissionId(75L); req.setPatientId(999L);
        req.setSurgeryName("测试"); req.setDiagnosis("测试");
        req.setPlannedDate(java.time.LocalDate.now()); req.setAnesthesiaMethod(1);
        var e = assertThrows(BizException.class, () -> svc().create(req));
        assertTrue(e.getMessage().contains("不匹配"));
    }

    // ---- 门禁：缺核查单禁止开始手术（服务端硬校验） ----
    @Test
    void start_blocksWithoutChecks() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 30));
        when(checkMapper.selectCount(any())).thenReturn(0L);
        var e = assertThrows(BizException.class, () -> svc().start(1L));
        assertTrue(e.getMessage().contains("核查"));
    }

    // ---- 状态机：术中才能保存麻醉记录 ----
    @Test
    void anesthesia_rejectsNonSurgeryStatus() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 30));
        var e = assertThrows(BizException.class,
                () -> svc().saveAnesthesia(1L, new com.his.modules.ors.dto.AnesthesiaRequest() {{ setAsaGrade(2); }}));
        assertTrue(e.getMessage().contains("术中"));
    }

    // ---- 状态机：完成关档须先离室 ----
    @Test
    void complete_rejectsBeforeLeave() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 40));
        BizException e = assertThrows(BizException.class, () -> svc().complete(1L));
        assertTrue(e.getMessage().contains("离室"));
    }

    // ---- 状态机：取消限待审核/已审核/已排台 ----
    @Test
    void cancel_rejectsAfterStart() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 40));
        BizException e = assertThrows(BizException.class, () -> svc().cancel(1L, "test"));
        assertTrue(e.getMessage().contains("才可取消"));
    }
}
