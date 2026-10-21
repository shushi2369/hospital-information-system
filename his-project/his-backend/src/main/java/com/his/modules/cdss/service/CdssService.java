package com.his.modules.cdss.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.cdss.dto.CdssQuery;
import com.his.modules.cdss.entity.CdssHit;
import com.his.modules.cdss.entity.CdssRule;
import com.his.modules.cdss.mapper.CdssHitMapper;
import com.his.modules.cdss.mapper.CdssRuleMapper;
import com.his.modules.doc.entity.DocOrder;
import com.his.modules.doc.entity.DocOrderItem;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CDSS 规则引擎服务（《18》§2.3）：开单后置提示（提示级，不阻断）+ 命中留痕。
 * check 由 doc 开单事务后置调用：REQUIRES_NEW 独立事务，方法内不抛异常（异常仅记日志），
 * 保证 CDSS 故障不影响开单主链路（20 号 §1 高风险改动点）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CdssService {
    private final CdssRuleMapper ruleMapper;
    private final CdssHitMapper hitMapper;
    private final com.his.modules.doc.mapper.DocOrderMapper orderMapper;
    private final com.his.modules.basedata.app.BasedataAppService basedataAppService;
    private final com.his.modules.patient.app.PatientAppService patientAppService;
    private final PltService pltService;
    private final IdGenerator idGenerator;

    /** 规则分页（C-01 查询侧） */
    public PageResult<CdssRule> rulePage(CdssQuery query) {
        Page<CdssRule> page = ruleMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<CdssRule>()
                        .eq(query.getRuleType() != null, CdssRule::getRuleType, query.getRuleType())
                        .orderByDesc(CdssRule::getId));
        return PageResult.of(page);
    }

    /** 规则创建（C-01） */
    public String createRule(CdssRule rule) {
        if (rule.getRuleType() == null || rule.getRuleType() < 1 || rule.getRuleType() > 4) {
            throw new BizException(ErrorCode.A0001, "规则类型取值 1~4");
        }
        if (rule.getRuleCode() == null || rule.getRuleCode().isBlank()
                || rule.getRuleCode().length() > 32) {
            throw new BizException(ErrorCode.A0001, "规则编码不能为空且最长 32 字");
        }
        if (rule.getMessage() == null || rule.getMessage().isBlank()
                || rule.getMessage().length() > 256) {
            throw new BizException(ErrorCode.A0001, "提示文案不能为空且最长 256 字");
        }
        if (rule.getRefAId() == null) {
            throw new BizException(ErrorCode.A0001, "参照A不能为空");
        }
        validateRuleFields(rule);
        // 裸实体入参：主键/状态/审计列必须由服务端生成
        rule.setId(null);
        rule.setStatus(1);
        rule.setVersion(0);
        rule.setCreatedBy(null);
        rule.setCreatedAt(null);
        rule.setUpdatedAt(null);
        try {
            ruleMapper.insert(rule);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.A0001, "规则编码已存在");
        }
        return rule.getRuleCode();
    }

    /** 规则更新（C-01）：枚举字段与服务端白名单校验（裸收 status=99 会让规则被 eq(status,1) 静默排除） */
    public void updateRule(Long id, CdssRule req) {
        if (req.getRuleType() != null && (req.getRuleType() < 1 || req.getRuleType() > 4)) {
            throw new BizException(ErrorCode.A0001, "规则类型取值 1~4");
        }
        if (req.getStatus() != null && (req.getStatus() < 0 || req.getStatus() > 1)) {
            throw new BizException(ErrorCode.A0001, "状态取值 0 停用 / 1 启用");
        }
        if (req.getLevel() != null && req.getLevel() < 1) {
            throw new BizException(ErrorCode.A0001, "提示级别须为正整数（当前口径 1=提示）");
        }
        CdssRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.A0001, "规则不存在");
        }
        // 一百一十六轮 cdss【1】P1：null 语义改"跳过该字段"——原缺省重置为默认值
        // 会让局部修改（如只改文案）把已停用规则静默重新启用（CDSS 命中复活）
        if (req.getRuleType() != null) {
            rule.setRuleType(req.getRuleType());
        }
        if (req.getRefAId() != null) {
            rule.setRefAId(req.getRefAId());
        }
        if (req.getRefBId() != null) {
            rule.setRefBId(req.getRefBId());
        }
        if (req.getAllergyKeyword() != null) {
            rule.setAllergyKeyword(req.getAllergyKeyword());
        }
        if (req.getAgeMin() != null) {
            rule.setAgeMin(req.getAgeMin());
        }
        if (req.getAgeMax() != null) {
            rule.setAgeMax(req.getAgeMax());
        }
        if (req.getLevel() != null) {
            rule.setLevel(req.getLevel());
        }
        if (req.getMessage() != null) {
            rule.setMessage(req.getMessage());
        }
        if (req.getStatus() != null) {
            rule.setStatus(req.getStatus());
        }
        // 合并后校验：原实现只校验请求体，{refAId=既有refBId} 可把配伍规则改为自配对死规则，
        // 空文案也能落库（创建侧有拦截、更新侧没有）
        if (rule.getMessage() == null || rule.getMessage().isBlank() || rule.getMessage().length() > 256) {
            throw new BizException(ErrorCode.A0001, "提示文案不能为空且最长 256 字");
        }
        validateRuleFields(rule);
        if (ruleMapper.updateById(rule) != 1) {
            throw new BizException(ErrorCode.A0008, "规则已变化，请刷新后重试");
        }
    }

    /** 一百一十六轮 cdss【8】：类型依赖字段校验——防"静默死规则"（启用却永不命中） */
    private void validateRuleFields(CdssRule req) {
        if (req.getRuleType() != null && req.getRuleType() == 1
                && (req.getRefBId() == null || req.getRefBId().equals(req.getRefAId()))) {
            throw new BizException(ErrorCode.A0001, "配伍规则必须指定参照B且不得与参照A相同");
        }
        if (req.getRuleType() != null && req.getRuleType() == 3 && req.getRefBId() == null) {
            throw new BizException(ErrorCode.A0001, "年龄规则必须指定参照B（年龄段上限口径）");
        }
        if (req.getRuleType() != null && req.getRuleType() == 4
                && (req.getAllergyKeyword() == null || req.getAllergyKeyword().isBlank())) {
            throw new BizException(ErrorCode.A0001, "过敏史规则必须填写关键词");
        }
        if (req.getAgeMin() != null && req.getAgeMax() != null && req.getAgeMin() > req.getAgeMax()) {
            throw new BizException(ErrorCode.A0001, "年龄下限不能大于上限");
        }
    }

    /** 命中分页（C-02）：医生数据范围限本人开立医嘱的命中（冗余 doctor_id 过滤，管理员全量）。
     *  doctor_id 为 bas_doctor.id，须经 getDoctorByUserId 做 sys_user→doctor 映射（ID 体系不同）。 */
    /** 命中列表开单医生名回填（一百零二轮裸 ID 清查 #4；doctorId 是 bas_doctor.id） */
    private void fillDoctorNames(Page<CdssHit> page) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (CdssHit h : page.getRecords()) {
            if (h.getDoctorId() != null) ids.add(h.getDoctorId());
        }
        if (ids.isEmpty()) return;
        for (var d : basedataAppService.listDoctorsByIds(ids).values()) {
            for (CdssHit h : page.getRecords()) {
                if (d.getId().equals(h.getDoctorId())) h.setDoctorName(d.getDoctorName());
            }
        }
    }

    /** 一百一十六轮 cdss【10】：命中处置回写——ignored 0=提示后放弃（原全系统无写入路径，
     *  schema 承诺的处置语义是死代码）。仅本人医嘱的命中可回写。 */
    @Transactional
    public void acknowledge(Long hitId, boolean ignored) {
        CdssHit hit = hitMapper.selectById(hitId);
        if (hit == null) {
            throw new BizException(ErrorCode.A0001, "CDSS 命中记录不存在");
        }
        var order = orderMapper.selectById(hit.getOrderId());
        boolean admin = CurrentUser.get().getRoleCodes().contains("ADMIN");
        // order.doctorId 是 bas_doctor.id，CurrentUser.id() 是 sys_user.id——须先换算再比对
        var doctor = admin ? null : basedataAppService.getDoctorByUserId(CurrentUser.id());
        if (!admin && (order == null || doctor == null || !doctor.getId().equals(order.getDoctorId()))) {
            throw new BizException(ErrorCode.A0003, "仅开单医生本人可处置该命中");
        }
        int updated = hitMapper.update(null, new LambdaUpdateWrapper<CdssHit>()
                .eq(CdssHit::getId, hitId)
                .set(CdssHit::getIgnored, ignored ? 1 : 0));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0008, "命中记录已变化，请刷新后重试");
        }
    }

    public PageResult<CdssHit> hitPage(CdssQuery query) {
        boolean admin = CurrentUser.get().getRoleCodes().contains("ADMIN");
        if (!admin) {
            com.his.modules.basedata.app.DoctorDTO doctor =
                    basedataAppService.getDoctorByUserId(CurrentUser.id());
            if (doctor == null) {
                Page<CdssHit> empty = new Page<>(query.getPageNum(), query.getPageSize());
                return PageResult.of(empty);
            }
            Page<CdssHit> page = hitMapper.selectPage(query.toPage(),
                    new LambdaQueryWrapper<CdssHit>()
                            .eq(CdssHit::getDoctorId, doctor.getId())
                            .eq(query.getOrderId() != null, CdssHit::getOrderId, query.getOrderId())
                            .orderByDesc(CdssHit::getId));
            fillDoctorNames(page);
            return PageResult.of(page);
        }
        Page<CdssHit> page = hitMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<CdssHit>()
                        .eq(query.getOrderId() != null, CdssHit::getOrderId, query.getOrderId())
                        .orderByDesc(CdssHit::getId));
        fillDoctorNames(page);
        return PageResult.of(page);
    }

    /**
     * 开单后置校验（doc → cdss 单向 hook，《19》§6）：命中写留痕，不抛异常、不阻断开单。
     * REQUIRES_NEW：与开单事务解耦；items 由 doc 传入（同 LIS 参数传递模式，cdss 不读 doc 表）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void check(DocOrder order, List<DocOrderItem> items) {
        try {
            List<CdssRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<CdssRule>()
                    .eq(CdssRule::getStatus, 1));
            if (rules.isEmpty() || items == null || items.isEmpty()) {
                return;
            }
            List<Long> hitIds = new ArrayList<>();
            // 患者档案一次读取（过敏守门 + type=4 精确映射 + type=3 年龄维度共用）
            com.his.modules.patient.app.PatientDTO patient =
                    order.getPatientId() == null ? null : patientAppService.getById(order.getPatientId());
            String allergyHistory = patient == null ? null : patient.getAllergyHistory();
            Integer patientAge = calcAge(patient == null ? null : patient.getBirthDate());
            // 过敏史守门提示（患者安全，业界 HIS 高频缺陷对标）：
            // 药品医嘱 + 患者过敏史非空 → 患者级命中（不依赖规则配置，开药即查）
            if (order.getCategory() != null && order.getCategory() == 1 && order.getPatientId() != null) {
                String allergy = allergyHistory;
                if (allergy != null && !allergy.isBlank()) {
                    CdssHit record = new CdssHit();
                    record.setOrderId(order.getId());
                    record.setDoctorId(order.getDoctorId());
                    record.setRuleId(0L); // 0=患者级过敏史提示（非规则表项）
                    String msg = "患者存在过敏史记录：" + allergy + "，请核对用药";
                    record.setMessage(msg.length() > 256 ? msg.substring(0, 256) : msg);
                    record.setIgnored(1);
                    record.setHitTime(LocalDateTime.now());
                    hitMapper.insert(record);
                    hitIds.add(record.getId());
                }
            }
            for (CdssRule rule : rules) {
            try {
                // 四期 type=4：过敏原-药品精确映射（患者过敏史包含关键字 且 医嘱含目标药品）
                if (rule.getRuleType() != null && rule.getRuleType() == 4) {
                    String hit = matchAllergy(rule, items, allergyHistory);
                    if (hit != null) {
                        CdssHit record = new CdssHit();
                        record.setOrderId(order.getId());
                        record.setDoctorId(order.getDoctorId());
                        record.setRuleId(rule.getId());
                        record.setMessage(hit);
                        record.setIgnored(1);
                        record.setHitTime(LocalDateTime.now());
                        hitMapper.insert(record);
                        hitIds.add(record.getId());
                    }
                    continue;
                }
                // 四期 type=3 年龄维度：规则限定年龄段时，患者年龄不在区间则不生效
                if (rule.getRuleType() != null && rule.getRuleType() == 3
                        && (rule.getAgeMin() != null || rule.getAgeMax() != null)) {
                    if (patientAge == null
                            || (rule.getAgeMin() != null && patientAge < rule.getAgeMin())
                            || (rule.getAgeMax() != null && patientAge > rule.getAgeMax())) {
                        continue;
                    }
                }
                String hit = match(rule, items);
                if (hit != null) {
                    CdssHit record = new CdssHit();
                    record.setOrderId(order.getId());
                    record.setDoctorId(order.getDoctorId());
                    record.setRuleId(rule.getId());
                    record.setMessage(hit);
                    record.setIgnored(1); // hook 在开单成功后调用：默认坚持开立
                    record.setHitTime(LocalDateTime.now());
                    hitMapper.insert(record);
                    hitIds.add(record.getId());
                }
            } catch (Exception e) {
                // 一百一十六轮 cdss【9】：单条规则异常只跳过本条并留痕——原整循环被
                // 外层 catch 连坐，一条脏规则让该医嘱其余所有 CDSS 校验静默失效
                pltService.recordEvent("cdss.rule.error", order.getOrderNo(),
                        "{\"ruleId\":" + rule.getId() + "}");
            }
        }
            if (!hitIds.isEmpty()) {
                pltService.recordEvent("cdss.hit", order.getOrderNo(),
                        "{\"hits\":" + hitIds.size() + "}");
            }
        } catch (Exception e) {
            // 异常隔离：CDSS 故障只记日志，不向开单事务传播
            log.warn("CDSS check failed for order {}: {}", order.getId(), e.getMessage());
        }
    }

    /** 四期 type=4：过敏原关键字匹配（患者过敏史包含关键字 且 医嘱含目标药品） */
    private String matchAllergy(CdssRule rule, List<DocOrderItem> items, String allergyHistory) {
        if (rule.getAllergyKeyword() == null || rule.getAllergyKeyword().isBlank()
                || allergyHistory == null || !allergyHistory.contains(rule.getAllergyKeyword())) {
            return null;
        }
        boolean hasDrug = items.stream().anyMatch(i -> rule.getRefAId().equals(i.getDrugId()));
        return hasDrug ? rule.getMessage() + "（患者过敏史含【" + rule.getAllergyKeyword() + "】）" : null;
    }

    /** 年龄现算（出生日期→周岁） */
    private Integer calcAge(java.time.LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return java.time.Period.between(birthDate, java.time.LocalDate.now()).getYears();
    }

    /** 规则匹配（提示级）：返回命中提示文案，未命中返回 null */
    private String match(CdssRule rule, List<DocOrderItem> items) {
        switch (rule.getRuleType()) {
            case 1 -> {
                // 配伍禁忌：同一药品医嘱同时含 ref_a 与 ref_b
                if (items.size() < 2) {
                    return null;
                }
                boolean hasA = items.stream().anyMatch(i -> rule.getRefAId().equals(i.getDrugId()));
                boolean hasB = items.stream().anyMatch(i -> rule.getRefBId() != null && rule.getRefBId().equals(i.getDrugId()));
                return hasA && hasB ? rule.getMessage() : null;
            }
            case 2 -> {
                // 重复检查：非药品医嘱明细引用了规则项目
                boolean dup = items.stream().anyMatch(i -> rule.getRefAId().equals(i.getChargeItemId()));
                return dup ? rule.getMessage() : null;
            }
            case 3 -> {
                // 剂量上限：药品明细数量超过 ref_b（整型化比较）
                if (rule.getRefBId() == null) {
                    return null;
                }
                boolean over = items.stream().anyMatch(i -> rule.getRefAId().equals(i.getDrugId())
                        && i.getQuantity() != null
                        && i.getQuantity().doubleValue() > rule.getRefBId().doubleValue());
                return over ? rule.getMessage() : null;
            }
            default -> {
                return null;
            }
        }
    }
}
