package com.his.modules.cdss.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        rule.setId(null);
        rule.setStatus(1);
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
        rule.setRuleType(req.getRuleType());
        rule.setRefAId(req.getRefAId());
        rule.setRefBId(req.getRefBId());
        rule.setAllergyKeyword(req.getAllergyKeyword());
        rule.setAgeMin(req.getAgeMin());
        rule.setAgeMax(req.getAgeMax());
        rule.setLevel(req.getLevel() == null ? 1 : req.getLevel());
        rule.setMessage(req.getMessage());
        rule.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        if (ruleMapper.updateById(rule) != 1) {
            throw new BizException(ErrorCode.A0008, "规则已变化，请刷新后重试");
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
