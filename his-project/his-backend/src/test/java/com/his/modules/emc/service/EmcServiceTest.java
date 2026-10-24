package com.his.modules.emc.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.clinic.mapper.CliVisitMapper;
import com.his.modules.emc.dto.CloseRequest;
import com.his.modules.emc.dto.LinkRequest;
import com.his.modules.emc.dto.TimepointRequest;
import com.his.modules.emc.dto.TriageRequest;
import com.his.modules.emc.dto.VisitCreateRequest;
import com.his.modules.emc.entity.EmcNodeDict;
import com.his.modules.emc.entity.EmcTriage;
import com.his.modules.emc.entity.EmcTimepoint;
import com.his.modules.emc.entity.EmcVisit;
import com.his.modules.emc.mapper.EmcNodeDictMapper;
import com.his.modules.emc.mapper.EmcTimepointMapper;
import com.his.modules.emc.mapper.EmcTriageMapper;
import com.his.modules.emc.mapper.EmcVisitMapper;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.plt.service.PltService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** EmcService 单测：五大中心登记一致性/节点字典与时限校验/跨患者挂单 IDOR/关档行锁与分诊同步。 */
class EmcServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, EmcTriage.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, EmcVisit.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, EmcNodeDict.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, EmcTimepoint.class);
    }

    private final EmcTriageMapper triageMapper = mock(EmcTriageMapper.class);
    private final EmcVisitMapper visitMapper = mock(EmcVisitMapper.class);
    private final EmcTimepointMapper timepointMapper = mock(EmcTimepointMapper.class);
    private final EmcNodeDictMapper nodeDictMapper = mock(EmcNodeDictMapper.class);
    private final PatientAppService patientAppService = mock(PatientAppService.class);
    private final InpAppService inpAppService = mock(InpAppService.class);
    private final CliVisitMapper clinicVisitMapper = mock(CliVisitMapper.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private EmcService svc() {
        return new EmcService(triageMapper, visitMapper, timepointMapper, nodeDictMapper,
                patientAppService, inpAppService, clinicVisitMapper, pltService, idGenerator);
    }

    private EmcTriage triage(Integer centerType) {
        EmcTriage t = new EmcTriage();
        t.setId(1L);
        t.setTriageNo("FZ001");
        t.setPatientId(11L);
        t.setCenterType(centerType);
        t.setStatus(1);
        return t;
    }

    private EmcVisit visit(int status, LocalDateTime start) {
        EmcVisit v = new EmcVisit();
        v.setId(10L);
        v.setVisitNo("JZ001");
        v.setTriageId(1L);
        v.setPatientId(11L);
        v.setCenterType(1);
        v.setStartTime(start);
        v.setStatus(status);
        return v;
    }

    private EmcNodeDict node(int centerType, int status) {
        EmcNodeDict n = new EmcNodeDict();
        n.setNodeCode("N_ARRIVE");
        n.setCenterType(centerType);
        n.setStatus(status);
        return n;
    }

    @Test
    void triage_levelOutOfRange_throws() {
        TriageRequest req = new TriageRequest();
        req.setTriageLevel(5);
        BizException e = assertThrows(BizException.class, () -> svc().triage(req));
        assertTrue(e.getMessage().contains("1~4"));
    }

    @Test
    void triage_happy_defaultsCenterAndChannel() {
        TriageRequest req = new TriageRequest();
        req.setPatientId(11L);
        req.setTriageLevel(2);
        when(idGenerator.next("FZ")).thenReturn("FZ001");

        String no = svc().triage(req);

        assertEquals("FZ001", no);
        verify(pltService).recordEvent(eq("emc.triage.created"), eq("FZ001"), contains("\"level\":2"));
    }

    @Test
    void createVisit_triageMissing_throws() {
        when(triageMapper.selectById(1L)).thenReturn(null);
        VisitCreateRequest req = new VisitCreateRequest();
        req.setTriageId(1L);
        req.setCenterType(1);
        BizException e = assertThrows(BizException.class, () -> svc().createVisit(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void createVisit_centerMismatch_throws() {
        when(triageMapper.selectById(1L)).thenReturn(triage(1));
        VisitCreateRequest req = new VisitCreateRequest();
        req.setTriageId(1L);
        req.setCenterType(2);
        BizException e = assertThrows(BizException.class, () -> svc().createVisit(req));
        assertTrue(e.getMessage().contains("不一致"));
    }

    @Test
    void createVisit_duplicate_throws() {
        when(triageMapper.selectById(1L)).thenReturn(triage(1));
        when(visitMapper.selectCount(any())).thenReturn(1L);
        VisitCreateRequest req = new VisitCreateRequest();
        req.setTriageId(1L);
        req.setCenterType(1);
        BizException e = assertThrows(BizException.class, () -> svc().createVisit(req));
        assertTrue(e.getMessage().contains("已登记"));
    }

    @Test
    void createVisit_happy_copiesPatientAndStatus10() {
        when(triageMapper.selectById(1L)).thenReturn(triage(1));
        when(visitMapper.selectCount(any())).thenReturn(0L);
        when(idGenerator.next("JZ")).thenReturn("JZ001");
        VisitCreateRequest req = new VisitCreateRequest();
        req.setTriageId(1L);
        req.setCenterType(1);
        req.setDoctorId(5L);

        String no = svc().createVisit(req);

        assertEquals("JZ001", no);
        org.mockito.ArgumentCaptor<EmcVisit> captor =
                org.mockito.ArgumentCaptor.forClass(EmcVisit.class);
        verify(visitMapper).insert(captor.capture());
        assertEquals(11L, captor.getValue().getPatientId());
        assertEquals(10, captor.getValue().getStatus());
        // 截断到秒：DATETIME(0) 四舍五入会让"节点早于登记"校验对同秒节点误拦
        assertEquals(0, captor.getValue().getStartTime().getNano());
    }

    @Test
    void createVisit_raceDuplicateKey_throws() {
        when(triageMapper.selectById(1L)).thenReturn(triage(1));
        when(visitMapper.selectCount(any())).thenReturn(0L);
        when(visitMapper.insert(any(EmcVisit.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk"));
        VisitCreateRequest req = new VisitCreateRequest();
        req.setTriageId(1L);
        req.setCenterType(1);
        BizException e = assertThrows(BizException.class, () -> svc().createVisit(req));
        assertTrue(e.getMessage().contains("已登记"));
    }

    @Test
    void addTimepoint_visitMissing_throws() {
        when(visitMapper.selectByIdForUpdate(10L)).thenReturn(null);
        TimepointRequest req = new TimepointRequest();
        BizException e = assertThrows(BizException.class, () -> svc().addTimepoint(10L, req));
        assertTrue(e.getMessage().contains("不存在"));
    }

    @Test
    void addTimepoint_closedVisit_throws() {
        when(visitMapper.selectByIdForUpdate(10L)).thenReturn(visit(20, LocalDateTime.now()));
        TimepointRequest req = new TimepointRequest();
        BizException e = assertThrows(BizException.class, () -> svc().addTimepoint(10L, req));
        assertTrue(e.getMessage().contains("已关档"));
    }

    @Test
    void addTimepoint_nodeNotInDictOrDisabled_throws() {
        when(visitMapper.selectByIdForUpdate(10L)).thenReturn(visit(10, LocalDateTime.now()));
        when(nodeDictMapper.selectOne(any())).thenReturn(null);
        TimepointRequest req = new TimepointRequest();
        req.setNodeCode("N_GHOST");
        BizException e = assertThrows(BizException.class, () -> svc().addTimepoint(10L, req));
        assertTrue(e.getMessage().contains("字典"));

        when(nodeDictMapper.selectOne(any())).thenReturn(node(1, 0));
        BizException e2 = assertThrows(BizException.class, () -> svc().addTimepoint(10L, req));
        assertTrue(e2.getMessage().contains("字典"));
    }

    @Test
    void addTimepoint_nodeOfOtherCenter_throws() {
        when(visitMapper.selectByIdForUpdate(10L)).thenReturn(visit(10, LocalDateTime.now()));
        when(nodeDictMapper.selectOne(any())).thenReturn(node(2, 1));
        TimepointRequest req = new TimepointRequest();
        req.setNodeCode("N_ARRIVE");
        BizException e = assertThrows(BizException.class, () -> svc().addTimepoint(10L, req));
        assertTrue(e.getMessage().contains("不属于"));
    }

    @Test
    void addTimepoint_beforeStart_throws() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(30);
        when(visitMapper.selectByIdForUpdate(10L)).thenReturn(visit(10, start));
        when(nodeDictMapper.selectOne(any())).thenReturn(node(1, 1));
        TimepointRequest req = new TimepointRequest();
        req.setNodeCode("N_ARRIVE");
        req.setNodeTime(start.minusMinutes(1).plusNanos(500_000_000L));
        BizException e = assertThrows(BizException.class, () -> svc().addTimepoint(10L, req));
        assertTrue(e.getMessage().contains("早于登记时刻"));
    }

    @Test
    void addTimepoint_happy() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(30);
        when(visitMapper.selectByIdForUpdate(10L)).thenReturn(visit(10, start));
        when(nodeDictMapper.selectOne(any())).thenReturn(node(1, 1));
        TimepointRequest req = new TimepointRequest();
        req.setNodeCode("N_ARRIVE");
        req.setNodeTime(LocalDateTime.now());

        // 返回主键由 MyBatis 回填，mock 不回填；仅验证节点落库
        svc().addTimepoint(10L, req);
        verify(timepointMapper).insert(any(EmcTimepoint.class));
    }

    @Test
    void addTimepoint_duplicateNode_throws() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(30);
        when(visitMapper.selectByIdForUpdate(10L)).thenReturn(visit(10, start));
        when(nodeDictMapper.selectOne(any())).thenReturn(node(1, 1));
        when(timepointMapper.insert(any(EmcTimepoint.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk"));
        TimepointRequest req = new TimepointRequest();
        req.setNodeCode("N_ARRIVE");
        req.setNodeTime(LocalDateTime.now());
        BizException e = assertThrows(BizException.class, () -> svc().addTimepoint(10L, req));
        assertTrue(e.getMessage().contains("已录入"));
    }

    @Test
    void link_closedVisitFrozen_throws() {
        when(visitMapper.selectById(10L)).thenReturn(visit(20, LocalDateTime.now()));
        LinkRequest req = new LinkRequest();
        req.setAdmissionId(900L);
        BizException e = assertThrows(BizException.class, () -> svc().link(10L, req));
        assertTrue(e.getMessage().contains("冻结") || e.getMessage().contains("不可修改"));
    }

    @Test
    void link_crossPatientAdmission_throws() {
        // 八十八轮 IDOR 审计 P1-2：跨患者挂单禁止
        EmcVisit v = visit(10, LocalDateTime.now());
        when(visitMapper.selectById(10L)).thenReturn(v);
        InpAdmission admission = new InpAdmission();
        admission.setId(900L);
        admission.setPatientId(99L);
        when(inpAppService.requireAdmission(900L)).thenReturn(admission);
        LinkRequest req = new LinkRequest();
        req.setAdmissionId(900L);
        BizException e = assertThrows(BizException.class, () -> svc().link(10L, req));
        assertTrue(e.getMessage().contains("跨患者"));
    }

    @Test
    void link_happy_updatesVisit() {
        EmcVisit v = visit(10, LocalDateTime.now());
        when(visitMapper.selectById(10L)).thenReturn(v);
        InpAdmission admission = new InpAdmission();
        admission.setId(900L);
        admission.setPatientId(11L);
        when(inpAppService.requireAdmission(900L)).thenReturn(admission);
        when(visitMapper.updateById(any(EmcVisit.class))).thenReturn(1);
        LinkRequest req = new LinkRequest();
        req.setAdmissionId(900L);

        assertDoesNotThrow(() -> svc().link(10L, req));
        assertEquals(900L, v.getAdmissionId());
    }

    @Test
    void close_happy_syncsTriageToClosed() {
        // 关档须同步分诊单 status 1→2（原 emc_triage.status=2 无写入方，"已关档"筛选永远为空）
        EmcVisit v = visit(10, LocalDateTime.now());
        when(visitMapper.selectByIdForUpdate(10L)).thenReturn(v);
        when(visitMapper.updateById(any(EmcVisit.class))).thenReturn(1);
        CloseRequest req = new CloseRequest();
        req.setOutcome(1);

        assertDoesNotThrow(() -> svc().close(10L, req));

        assertEquals(20, v.getStatus());
        assertNotNull(v.getOutcomeTime());
        verify(triageMapper).update(isNull(), any());
        verify(pltService).recordEvent(eq("emc.visit.closed"), eq("JZ001"), anyString());
    }

    @Test
    void close_concurrentZeroRow_throws() {
        when(visitMapper.selectByIdForUpdate(10L)).thenReturn(visit(10, LocalDateTime.now()));
        when(visitMapper.updateById(any(EmcVisit.class))).thenReturn(0);
        CloseRequest req = new CloseRequest();
        req.setOutcome(1);
        BizException e = assertThrows(BizException.class, () -> svc().close(10L, req));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void close_alreadyClosed_throws() {
        when(visitMapper.selectByIdForUpdate(10L)).thenReturn(visit(20, LocalDateTime.now()));
        CloseRequest req = new CloseRequest();
        req.setOutcome(1);
        BizException e = assertThrows(BizException.class, () -> svc().close(10L, req));
        assertTrue(e.getMessage().contains("已关档"));
    }
}
