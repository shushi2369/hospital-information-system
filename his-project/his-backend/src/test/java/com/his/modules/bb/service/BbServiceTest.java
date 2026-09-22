package com.his.modules.bb.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.bb.dto.*;
import com.his.modules.bb.entity.*;
import com.his.modules.bb.mapper.*;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.plt.service.PltService;
import com.his.modules.system.app.SystemAppService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 输血闭环单测：三道安全门禁 + 状态机 + 归属校验 + 配血安全。 */
class BbServiceTest extends UnitTestBase {

    private final BbBloodBagMapper bagMapper = mock(BbBloodBagMapper.class);
    private final BbRequestMapper requestMapper = mock(BbRequestMapper.class);
    private final BbCrossMatchMapper crossMapper = mock(BbCrossMatchMapper.class);
    private final BbIssueMapper issueMapper = mock(BbIssueMapper.class);
    private final BbTransfusionMapper transfusionMapper = mock(BbTransfusionMapper.class);
    private final BbAdverseMapper adverseMapper = mock(BbAdverseMapper.class);
    private final InpAppService inpAppService = mock(InpAppService.class);
    private final SystemAppService systemAppService = mock(SystemAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGen = mock(IdGenerator.class);

    private BbService svc() {
        return new BbService(bagMapper, requestMapper, crossMapper, issueMapper,
                transfusionMapper, adverseMapper, inpAppService, systemAppService,
                pltService, idGen);
    }

    // ---- 工厂 ----
    private BbRequest request(Long id, int status) {
        var r = new BbRequest();
        r.setId(id); r.setReqNo("XY"+id); r.setAdmissionId(75L); r.setPatientId(415L);
        r.setBloodType(4); r.setRh(1); r.setComponent(1); r.setVolumeMl(200);
        r.setStatus(status); r.setDoctorId(2L);
        return r;
    }
    private BbBloodBag bag(Long id, int status) {
        var b = new BbBloodBag();
        b.setId(id); b.setBagNo("XDJ"+id); b.setBloodType(4); b.setRh(1);
        b.setComponent(1); b.setVolumeMl(200); b.setExpireDate(LocalDate.now().plusDays(30));
        b.setStatus(status);
        return b;
    }
    private BbCrossMatch match(Long bagId, int result) {
        var m = new BbCrossMatch();
        m.setRequestId(1L); m.setBagId(bagId); m.setCrossResult(result); m.setCrossMethod("盐水");
        return m;
    }
    private InpAdmission admission(Long id, Long pid) {
        var a = new InpAdmission();
        a.setId(id); a.setPatientId(pid); a.setStatus(10);
        return a;
    }

    // ---- 用血申请：跨患者拦截 ----
    @Test
    void create_rejectsCrossPatient() {
        when(inpAppService.requireInHospital(75L)).thenReturn(admission(75L, 415L));
        var req = new BbRequestCreateRequest();
        req.setAdmissionId(75L); req.setPatientId(999L);
        var e = assertThrows(BizException.class, () -> svc().createRequest(req));
        assertTrue(e.getMessage().contains("不匹配"));
    }

    // ---- 门禁①：不相容配血禁止发血（患者安全阻断） ----
    @Test
    void issue_blocksIncompatible() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 30));
        when(bagMapper.selectById(10L)).thenReturn(bag(10L, 1));
        when(crossMapper.selectList(any())).thenReturn(List.of(match(10L, 2)));
        var e = assertThrows(BizException.class,
                () -> svc().issue(1L, issueReq(10L)));
        assertTrue(e.getMessage().contains("不相容"));
    }

    // ---- 门禁①：无配血记录禁止发血 ----
    @Test
    void issue_blocksNoMatch() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 30));
        when(bagMapper.selectById(10L)).thenReturn(bag(10L, 1));
        when(crossMapper.selectList(any())).thenReturn(List.of());
        var e = assertThrows(BizException.class,
                () -> svc().issue(1L, issueReq(10L)));
        assertTrue(e.getMessage().contains("未做交叉配血"));
    }

    // ---- 门禁①：相容配血后发血成功 ----
    @Test
    void issue_successAfterCompatible() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 30));
        when(bagMapper.selectById(10L)).thenReturn(bag(10L, 1));
        when(crossMapper.selectList(any())).thenReturn(List.of(match(10L, 1)));
        Mockito.doAnswer(inv -> {
            var issue = inv.getArgument(0, BbIssue.class);
            issue.setId(999L);
            return 1;
        }).when(issueMapper).insert(any());
        when(systemAppService.getUsername(anyLong())).thenReturn("护士");
        Mockito.doReturn(1).when(requestMapper).updateById(any());
        Mockito.doReturn(1).when(bagMapper).updateById(any());
        setupAs(3L, "BB_USER");
        Long issueId = svc().issue(1L, issueReq(10L));
        assertNotNull(issueId);
    }

    // ---- 门禁②：取血护士须真实存在 ----
    @Test
    void issue_rejectsFakeReceiver() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 30));
        when(bagMapper.selectById(10L)).thenReturn(bag(10L, 1));
        when(crossMapper.selectList(any())).thenReturn(List.of(match(10L, 1)));
        when(systemAppService.getUsername(999L)).thenReturn(null);
        var e = assertThrows(BizException.class,
                () -> svc().issue(1L, issueReq(10L)));
        assertTrue(e.getMessage().contains("不存在"));
    }

    private com.his.modules.bb.dto.IssueRequest issueReq(long bagId) {
        var i = new com.his.modules.bb.dto.IssueRequest();
        i.setBagId(bagId); i.setReceiverId(2L);
        return i;
    }

    // ---- 门禁③：输注须床边双人双签（不得同一人） ----
    @Test
    void startTransfusion_rejectsSameSigner() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 40));
        when(issueMapper.selectCount(any())).thenReturn(1L);
        when(systemAppService.getUsername(1L)).thenReturn("ADMIN");
        var e = assertThrows(BizException.class,
                () -> svc().startTransfusion(1L, startReq(10L, 1L, 1L)));
        assertTrue(e.getMessage().contains("双人双签"));
    }

    // ---- 门禁③：核对签 2 须真实存在 ----
    @Test
    void startTransfusion_rejectsFakeSigner() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 40));
        when(issueMapper.selectCount(any())).thenReturn(1L);
        when(systemAppService.getUsername(999L)).thenReturn(null);
        var e = assertThrows(BizException.class,
                () -> svc().startTransfusion(1L, startReq(10L, 1L, 999L)));
        assertTrue(e.getMessage().contains("不存在"));
    }

    // ---- 门禁③：输注血袋须为本申请已发血的袋 ----
    @Test
    void startTransfusion_rejectsUnissuedBag() {
        var request = request(1L, 40);
        when(requestMapper.selectById(1L)).thenReturn(request);
        when(issueMapper.selectCount(any())).thenReturn(0L);
        when(systemAppService.getUsername(anyLong())).thenReturn("用户");
        var e = assertThrows(BizException.class,
                () -> svc().startTransfusion(1L, startReq(10L, 1L, 2L)));
        assertTrue(e.getMessage().contains("未发血"));
    }

    // ---- 门禁③：双签通过开始输注 ----
    @Test
    void startTransfusion_success() {
        var request = request(1L, 40);
        when(requestMapper.selectById(1L)).thenReturn(request);
        when(issueMapper.selectCount(any())).thenReturn(1L);
        when(systemAppService.getUsername(anyLong())).thenReturn("用户");
        Mockito.doAnswer(inv -> {
            var tf = inv.getArgument(0, BbTransfusion.class);
            tf.setId(888L);
            return 1;
        }).when(transfusionMapper).insert(any());
        Mockito.doReturn(1).when(requestMapper).updateById(any());
        setupAs(6L, "NURSE"); // 执行护士 userId=6
        Long tfId = svc().startTransfusion(1L, startReq(10L, null, 2L));
        assertNotNull(tfId);
    }

    private TransfusionStartRequest startReq(long bagId, Long checker1, Long checker2) {
        var t = new TransfusionStartRequest();
        t.setBagId(bagId);
        if (checker1 != null) t.setChecker1Id(checker1);
        t.setChecker2Id(checker2);
        t.setVitalBefore("T36.5");
        return t;
    }

    // ---- 状态机：取消限待审核/配血中 ----
    @Test
    void cancel_rejectsAfterIssue() {
        when(requestMapper.selectById(1L)).thenReturn(request(1L, 40));
        var e = assertThrows(BizException.class, () -> svc().cancel(1L));
        assertTrue(e.getMessage().contains("才可取消"));
    }

    // ---- 配血：血型/成分不匹配拦截 ----
    @Test
    void crossMatch_rejectsMismatch() {
        var request = request(1L, 20);
        request.setBloodType(4);
        when(requestMapper.selectById(1L)).thenReturn(request);
        var bag = bag(3L, 1); bag.setBloodType(1); bag.setComponent(2);
        when(bagMapper.selectById(3L)).thenReturn(bag);
        BizException e = assertThrows(BizException.class,
                () -> svc().crossMatch(1L, crossReq(3L, 1)));
        assertTrue(e.getMessage().contains("不匹配"));
    }

    // ---- 配血：Rh 不匹配拦截（患者安全） ----
    @Test
    void crossMatch_rejectsRhMismatch() {
        var request = request(1L, 20);
        request.setBloodType(4); request.setRh(2);
        when(requestMapper.selectById(1L)).thenReturn(request);
        var bag = bag(3L, 1); bag.setBloodType(4); bag.setRh(1); bag.setComponent(1);
        when(bagMapper.selectById(3L)).thenReturn(bag);
        BizException e = assertThrows(BizException.class,
                () -> svc().crossMatch(1L, crossReq(3L, 1)));
        assertTrue(e.getMessage().contains("Rh"));
    }

    private CrossMatchRequest crossReq(long bagId, int result) {
        var c = new CrossMatchRequest();
        c.setBagId(bagId); c.setCrossMethod("盐水"); c.setCrossResult(result);
        return c;
    }
}
