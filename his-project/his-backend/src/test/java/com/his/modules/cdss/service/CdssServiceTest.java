package com.his.modules.cdss.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.cdss.entity.CdssRule;
import com.his.modules.cdss.mapper.CdssHitMapper;
import com.his.modules.cdss.mapper.CdssRuleMapper;
import com.his.modules.plt.service.PltService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** CdssService 单测：updateRule null 跳过语义（116 轮 P1）+ 规则类型依赖校验（120 轮）+ 建码唯一。 */
class CdssServiceTest extends UnitTestBase {

    private final CdssRuleMapper ruleMapper = mock(CdssRuleMapper.class);
    private final CdssHitMapper hitMapper = mock(CdssHitMapper.class);
    private final com.his.modules.doc.mapper.DocOrderMapper orderMapper =
            mock(com.his.modules.doc.mapper.DocOrderMapper.class);
    private final com.his.modules.doc.mapper.DocOrderItemMapper itemMapper =
            mock(com.his.modules.doc.mapper.DocOrderItemMapper.class);
    private final com.his.modules.basedata.app.BasedataAppService basedataAppService =
            mock(com.his.modules.basedata.app.BasedataAppService.class);
    private final com.his.modules.patient.app.PatientAppService patientAppService =
            mock(com.his.modules.patient.app.PatientAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGen = mock(IdGenerator.class);

    private CdssService svc() {
        return new CdssService(ruleMapper, hitMapper, orderMapper, itemMapper,
                basedataAppService, patientAppService, pltService, idGen);
    }

    private CdssRule rule(Long id) {
        CdssRule r = new CdssRule();
        r.setId(id);
        r.setRuleCode("CDSS-T-" + id);
        r.setRuleType(2);
        r.setLevel(1);
        r.setMessage("原文案");
        r.setStatus(0); // 已停用——null 跳过语义的哨兵字段
        return r;
    }

    // ---------------- updateRule null 跳过（116 轮 P1：局部修改曾把停用规则静默重置为启用） ----------------

    @Test
    void updateRule_nullFieldsSkipNotReset() {
        CdssRule existing = rule(1L);
        when(ruleMapper.selectById(1L)).thenReturn(existing);
        when(ruleMapper.updateById(any())).thenReturn(1);
        CdssRule req = new CdssRule();
        req.setMessage("新文案"); // 只改文案
        svc().updateRule(1L, req);
        // null 字段全部保留原值——status 仍为 0（停用不被静默启用），level 不被重置
        assertEquals(0, existing.getStatus(), "null status 不得重置为启用");
        assertEquals(1, existing.getLevel(), "null level 不得重置为默认 1");
        assertEquals("新文案", existing.getMessage());
        verify(ruleMapper).updateById(existing);
    }

    @Test
    void updateRule_explicitFieldsApplied() {
        CdssRule existing = rule(1L);
        when(ruleMapper.selectById(1L)).thenReturn(existing);
        when(ruleMapper.updateById(any())).thenReturn(1);
        CdssRule req = new CdssRule();
        req.setStatus(1);
        svc().updateRule(1L, req);
        assertEquals(1, existing.getStatus(), "显式传值应生效");
    }

    @Test
    void updateRule_invalidStatusRejected() {
        when(ruleMapper.selectById(1L)).thenReturn(rule(1L));
        CdssRule req = new CdssRule();
        req.setStatus(9);
        BizException e = assertThrows(BizException.class, () -> svc().updateRule(1L, req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void updateRule_optimisticLockFailureThrows() {
        when(ruleMapper.selectById(1L)).thenReturn(rule(1L));
        when(ruleMapper.updateById(any())).thenReturn(0);
        CdssRule req = new CdssRule();
        req.setMessage("x");
        BizException e = assertThrows(BizException.class, () -> svc().updateRule(1L, req));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    // ---------------- 规则类型依赖校验（120 轮【8】：防静默死规则） ----------------

    @Test
    void createRule_interactionRequiresRefB() {
        CdssRule req = new CdssRule();
        req.setRuleCode("CDSS-XY-T1");
        req.setRuleType(1);
        req.setRefAId(1L); // 缺 refBId
        BizException e = assertThrows(BizException.class, () -> svc().createRule(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void createRule_interactionRefBEqualsRefARejected() {
        CdssRule req = new CdssRule();
        req.setRuleCode("CDSS-XY-T2");
        req.setRuleType(1);
        req.setRefAId(1L);
        req.setRefBId(1L); // 与 A 相同
        BizException e = assertThrows(BizException.class, () -> svc().createRule(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void createRule_allergyRequiresKeyword() {
        CdssRule req = new CdssRule();
        req.setRuleCode("CDSS-GM-T1");
        req.setRuleType(4);
        req.setAllergyKeyword("  "); // 空白关键词
        BizException e = assertThrows(BizException.class, () -> svc().createRule(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void createRule_invalidTypeRejected() {
        CdssRule req = new CdssRule();
        req.setRuleCode("CDSS-T9");
        req.setRuleType(9);
        BizException e = assertThrows(BizException.class, () -> svc().createRule(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void createRule_duplicateCodeRejected() {
        CdssRule req = new CdssRule();
        req.setRuleCode("CDSS-DUP");
        req.setRuleType(2);
        org.springframework.dao.DuplicateKeyException dk =
                new org.springframework.dao.DuplicateKeyException("uk");
        when(ruleMapper.insert(any())).thenThrow(dk);
        BizException e = assertThrows(BizException.class, () -> svc().createRule(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }
}
