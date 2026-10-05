package com.his.modules.pub.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.plt.service.PltService;
import com.his.modules.pub.entity.PubDiseaseDict;
import com.his.modules.pub.entity.PubHaiCase;
import com.his.modules.pub.entity.PubInfectiousCard;
import com.his.modules.pub.mapper.PubDiseaseDictMapper;
import com.his.modules.pub.mapper.PubHaiCaseMapper;
import com.his.modules.pub.mapper.PubInfectiousCardMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** PubService 单测：传染病自动报卡 + 上报闭环 + 院感确认。 */
class PubServiceTest extends UnitTestBase {

    private final PubInfectiousCardMapper cardMapper = mock(PubInfectiousCardMapper.class);
    private final PubDiseaseDictMapper dictMapper = mock(PubDiseaseDictMapper.class);
    private final PubHaiCaseMapper haiMapper = mock(PubHaiCaseMapper.class);
    private final PltService pltService = mock(PltService.class);
    private final com.his.modules.rpt.service.RptService rptService = mock(com.his.modules.rpt.service.RptService.class);
    private final com.his.modules.patient.app.PatientAppService patientAppService = mock(com.his.modules.patient.app.PatientAppService.class);
    private final IdGenerator idGen = mock(IdGenerator.class);

    private PubService svc() {
        return new PubService(cardMapper, dictMapper, haiMapper, pltService, idGen, rptService, patientAppService);
    }

    private PubDiseaseDict disease(String name, String cat) {
        var d = new PubDiseaseDict();
        d.setId(1L); d.setDiseaseName(name); d.setCategory(cat); d.setStatus(1);
        return d;
    }

    private PubInfectiousCard card(Long id, int status) {
        var c = new PubInfectiousCard();
        c.setId(id); c.setCardNo("CR" + id); c.setDiseaseName("肺结核");
        c.setDiseaseCategory("乙"); c.setStatus(status);
        return c;
    }

    // ---- 自动报卡：诊断匹配字典 → 自动生成待报卡 ----
    @Test
    void autoCreate_matchesDiagnosis() {
        when(dictMapper.selectList(any())).thenReturn(List.of(disease("肺结核", "乙")));
        svc().autoCreateCards(50L, null, 415L, 2L, "肺结核复查");
        Mockito.verify(cardMapper, Mockito.times(1)).insert(any());
    }

    // ---- 自动报卡：诊断不匹配 → 不生成 ----
    @Test
    void autoCreate_noMatchSkips() {
        when(dictMapper.selectList(any())).thenReturn(List.of(disease("肺结核", "乙")));
        svc().autoCreateCards(50L, null, 415L, 2L, "感冒");
        Mockito.verify(cardMapper, Mockito.never()).insert(any());
    }

    // ---- 自动报卡：诊断 null → 不生成、不抛异常 ----
    @Test
    void autoCreate_nullDiagnosisSafe() {
        assertDoesNotThrow(() -> svc().autoCreateCards(50L, null, 415L, 2L, null));
    }

    // ---- 上报：待报 → 已上报 ----
    @Test
    void report_success() {
        when(cardMapper.selectById(1L)).thenReturn(card(1L, 10));
        Mockito.doReturn(1).when(cardMapper).updateById(any());
        assertDoesNotThrow(() -> svc().report(1L));
    }

    // ---- 上报：重复上报拦截 ----
    @Test
    void report_rejectsDuplicate() {
        when(cardMapper.selectById(1L)).thenReturn(card(1L, 20));
        BizException e = assertThrows(BizException.class, () -> svc().report(1L));
        assertTrue(e.getMessage().contains("待报"));
    }

    // ---- 回执：未审核不能登记回执 ----
    @Test
    void receipt_rejectsBeforeApproval() {
        when(cardMapper.selectById(1L)).thenReturn(card(1L, 20));
        var req = new com.his.modules.pub.dto.ReceiptRequest();
        req.setReceiptNo("CDC001");
        BizException e = assertThrows(BizException.class, () -> svc().receipt(1L, req));
        assertTrue(e.getMessage().contains("未审核"));
    }

    // ---- 院感上报 ----
    @Test
    void haiReport_createsCase() {
        when(idGen.next("GR")).thenReturn("GR001");
        Mockito.when(haiMapper.insert(any())).thenReturn(1);
        var hai = new PubHaiCase();
        hai.setAdmissionId(75L); hai.setPatientId(415L);
        hai.setInfectionType(1); hai.setInfectionSite("呼吸道");
        String caseNo = svc().haiReport(hai);
        assertTrue(caseNo.startsWith("GR"));
    }

    // ---- 院感确认：状态推进 ----
    @Test
    void haiConfirm_progresses() {
        var hai = new PubHaiCase();
        hai.setId(1L); hai.setCaseNo("GR001"); hai.setStatus(10);
        when(haiMapper.selectById(1L)).thenReturn(hai);
        Mockito.doReturn(1).when(haiMapper).updateById(any());
        assertDoesNotThrow(() -> svc().haiConfirm(1L, "确认", 20));
    }
}
