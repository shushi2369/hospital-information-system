package com.his.modules.emc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.modules.emc.dto.*;
import com.his.modules.emc.entity.*;
import com.his.modules.emc.mapper.*;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EMC 急诊五大中心服务（《15》§2.3）：分诊 → 中心登记 → 时间节点 → 时限预警 → 转归关档 → 达标统计。
 * 达标率=节点时刻相对登记时刻分钟差对照节点字典 target_minutes（国考时限指标雏形）。
 */
@Service
@RequiredArgsConstructor
public class EmcService {
    private final EmcTriageMapper triageMapper;
    private final EmcVisitMapper visitMapper;
    private final EmcTimepointMapper timepointMapper;
    private final EmcNodeDictMapper nodeDictMapper;
    private final com.his.modules.patient.app.PatientAppService patientAppService;
    private final com.his.modules.inp.app.InpAppService inpAppService;
    private final com.his.modules.clinic.mapper.CliVisitMapper clinicVisitMapper;
    private final PltService pltService;
    private final IdGenerator idGenerator;

    /** 分诊登记（E-01） */
    @Transactional
    public String triage(TriageRequest req) {
        if (req.getTriageLevel() < 1 || req.getTriageLevel() > 4) {
            throw new BizException(ErrorCode.A0001, "分诊级别取值 1~4");
        }
        // 患者必须真实存在（防任意 ID 挂单）
        patientAppService.requireActive(req.getPatientId());
        EmcTriage triage = new EmcTriage();
        triage.setTriageNo(idGenerator.next("FZ"));
        triage.setPatientId(req.getPatientId());
        triage.setChiefComplaint(req.getChiefComplaint());
        triage.setBodyTemp(req.getBodyTemp());
        triage.setPulse(req.getPulse());
        triage.setRespiration(req.getRespiration());
        triage.setBloodPressure(req.getBloodPressure());
        triage.setSpo2(req.getSpo2());
        triage.setTriageLevel(req.getTriageLevel());
        triage.setCenterType(req.getCenterType() == null ? 0 : req.getCenterType());
        triage.setGreenChannel(req.getGreenChannel() == null ? 0 : req.getGreenChannel());
        triage.setTriageNurseId(CurrentUser.id());
        triage.setTriageTime(LocalDateTime.now());
        triage.setStatus(1);
        triageMapper.insert(triage);
        pltService.recordEvent("emc.triage.created", triage.getTriageNo(),
                "{\"level\":" + req.getTriageLevel() + ",\"green\":" + triage.getGreenChannel() + "}");
        return triage.getTriageNo();
    }

    /** 分诊列表（E-02）：附带已登记五大中心病例标记 */
    public PageResult<EmcTriage> pageTriage(EmcVisitQuery query) {
        Page<EmcTriage> page = triageMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<EmcTriage>()
                        .eq(query.getPatientId() != null, EmcTriage::getPatientId, query.getPatientId())
                        .eq(query.getCenterType() != null, EmcTriage::getCenterType, query.getCenterType())
                        .eq(query.getStatus() != null, EmcTriage::getStatus, query.getStatus())
                        .orderByDesc(EmcTriage::getId));
        if (!page.getRecords().isEmpty()) {
            java.util.Set<Long> triaged = visitMapper.selectList(new LambdaQueryWrapper<EmcVisit>()
                            .in(EmcVisit::getTriageId, page.getRecords().stream()
                                    .map(EmcTriage::getId).toList()))
                    .stream().map(EmcVisit::getTriageId).collect(java.util.stream.Collectors.toSet());
            page.getRecords().forEach(t -> t.setRegistered(triaged.contains(t.getId()) ? 1 : 0));
        }
        return PageResult.of(page);
    }

    /** 五大中心登记（E-03）：时限基准=登记时刻；与分诊预判中心一致性校验 */
    @Transactional
    public String createVisit(VisitCreateRequest req) {
        EmcTriage triage = triageMapper.selectById(req.getTriageId());
        if (triage == null) {
            throw new BizException(ErrorCode.A0001, "分诊单不存在");
        }
        if (triage.getCenterType() != null && triage.getCenterType() != 0
                && !triage.getCenterType().equals(req.getCenterType())) {
            throw new BizException(ErrorCode.A0001, "登记中心与分诊预判不一致");
        }
        Long exists = visitMapper.selectCount(new LambdaQueryWrapper<EmcVisit>()
                .eq(EmcVisit::getTriageId, req.getTriageId()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.A0001, "该分诊单已登记五大中心病例");
        }
        EmcVisit visit = new EmcVisit();
        visit.setVisitNo(idGenerator.next("JZ"));
        visit.setTriageId(req.getTriageId());
        visit.setCenterType(req.getCenterType());
        visit.setDoctorId(req.getDoctorId());
        visit.setPatientId(triage.getPatientId());
        visit.setVisitId(triage.getVisitId());
        // 截断到秒：MySQL DATETIME(0) 对带小数秒的值四舍五入会进位，导致
        // "节点时间早于登记时刻"校验对同秒节点误拦（十四轮查验回归）
        visit.setStartTime(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        visit.setStatus(10);
        try {
            visitMapper.insert(visit);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发双击撞 uk_emc_visit_triage：预检窗口兜底，语义化提示
            throw new BizException(ErrorCode.A0001, "该分诊单已登记五大中心病例");
        }
        pltService.recordEvent("emc.visit.registered", visit.getVisitNo(),
                "{\"center\":" + req.getCenterType() + "}");
        return visit.getVisitNo();
    }

    /** 病例分页（E-04） */
    public PageResult<EmcVisit> pageVisits(EmcVisitQuery query) {
        Page<EmcVisit> page = visitMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<EmcVisit>()
                        .eq(query.getPatientId() != null, EmcVisit::getPatientId, query.getPatientId())
                        .eq(query.getCenterType() != null, EmcVisit::getCenterType, query.getCenterType())
                        .eq(query.getStatus() != null, EmcVisit::getStatus, query.getStatus())
                        .orderByAsc(EmcVisit::getStatus).orderByDesc(EmcVisit::getId));
        return PageResult.of(page);
    }

    /** 病例详情（E-05）：含分诊、时间轴与达标计算 */
    public Map<String, Object> visitDetail(Long id) {
        EmcVisit visit = requireVisit(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("visit", visit);
        result.put("triage", triageMapper.selectById(visit.getTriageId()));
        result.put("timeline", timeline(id));
        return result;
    }

    /** 节点字典列表（前端录入下拉用） */
    public List<EmcNodeDict> listNodes() {
        return nodeDictMapper.selectList(new LambdaQueryWrapper<EmcNodeDict>()
                .eq(EmcNodeDict::getStatus, 1)
                .orderByAsc(EmcNodeDict::getCenterType).orderByAsc(EmcNodeDict::getSeqNo));
    }

    /** 时间节点录入（E-06）：字典校验（中心匹配且启用），超时同样允许录入（留痕真实时间） */
    @Transactional
    public Long addTimepoint(Long visitId, TimepointRequest req) {
        EmcVisit visit = requireVisit(visitId);
        if (visit.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "病例已关档，不可录入节点");
        }
        EmcNodeDict node = nodeDictMapper.selectOne(new LambdaQueryWrapper<EmcNodeDict>()
                .eq(EmcNodeDict::getNodeCode, req.getNodeCode()).last("LIMIT 1"));
        if (node == null || node.getStatus() == null || node.getStatus() != 1) {
            throw new BizException(ErrorCode.A0001, "节点编码不在字典中");
        }
        if (!node.getCenterType().equals(visit.getCenterType())) {
            throw new BizException(ErrorCode.A0001, "节点不属于该中心的节点集");
        }
        if (req.getNodeTime().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                .isBefore(visit.getStartTime())) {
            throw new BizException(ErrorCode.A0001, "节点时间早于登记时刻，请核实（时限以登记时刻为基准）");
        }
        EmcTimepoint tp = new EmcTimepoint();
        tp.setEmcVisitId(visitId);
        tp.setNodeCode(req.getNodeCode());
        tp.setNodeTime(req.getNodeTime());
        tp.setRecorderId(CurrentUser.id());
        tp.setNote(req.getNote());
        try {
            timepointMapper.insert(tp);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.A0001, "该节点已录入");
        }
        return tp.getId();
    }

    /** 后补关联（E-07）：住院/门诊单号须真实存在 */
    @Transactional
    public void link(Long visitId, LinkRequest req) {
        EmcVisit visit = requireVisit(visitId);
        // 关档(20)后关联单号冻结：达标统计口径不可事后改动（八十八轮状态机审计）
        if (visit.getStatus() == null || visit.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "病例已关档，关联信息不可修改");
        }
        if (req.getAdmissionId() != null) {
            com.his.modules.inp.entity.InpAdmission admission = inpAppService.requireAdmission(req.getAdmissionId());
            // 跨患者挂单校验（八十八轮 IDOR 审计 P1-2，对齐 Ors/Bb 口径）
            if (admission.getPatientId() == null || visit.getPatientId() == null
                    || !admission.getPatientId().equals(visit.getPatientId())) {
                throw new BizException(ErrorCode.A0001, "患者与住院登记不匹配，禁止跨患者挂单");
            }
            visit.setAdmissionId(req.getAdmissionId());
        }
        if (req.getVisitId() != null) {
            com.his.modules.clinic.entity.CliVisit opVisit = visitMapperById(req.getVisitId());
            if (opVisit == null || opVisit.getPatientId() == null || visit.getPatientId() == null
                    || !opVisit.getPatientId().equals(visit.getPatientId())) {
                throw new BizException(ErrorCode.A0001, "患者与门诊就诊不匹配，禁止跨患者挂单");
            }
            visit.setVisitId(req.getVisitId());
        }
        visitMapper.updateById(visit);
    }

    /** 关档（E-08）：转归登记，10 → 20 */
    @Transactional
    public void close(Long visitId, CloseRequest req) {
        EmcVisit visit = requireVisit(visitId);
        if (visit.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "病例已关档");
        }
        visit.setStatus(20);
        visit.setOutcome(req.getOutcome());
        visit.setOutcomeTime(LocalDateTime.now());
        if (visitMapper.updateById(visit) != 1) {
            throw new BizException(ErrorCode.A0008, "病例状态已变化，请刷新后重试");
        }
        pltService.recordEvent("emc.visit.closed", visit.getVisitNo(),
                "{\"outcome\":" + req.getOutcome() + "}");
    }

    /** 时间轴与达标计算（E-05）：elapsed=节点时刻-登记时刻（分钟）；onTarget=elapsed≤目标 */
    public List<Map<String, Object>> timeline(Long visitId) {
        EmcVisit visit = requireVisit(visitId);
        List<EmcNodeDict> dict = nodeDictMapper.selectList(new LambdaQueryWrapper<EmcNodeDict>()
                .eq(EmcNodeDict::getCenterType, visit.getCenterType())
                .eq(EmcNodeDict::getStatus, 1)
                .orderByAsc(EmcNodeDict::getSeqNo));
        Map<String, EmcTimepoint> recorded = timepointMapper.selectList(
                        new LambdaQueryWrapper<EmcTimepoint>().eq(EmcTimepoint::getEmcVisitId, visitId))
                .stream().collect(Collectors.toMap(EmcTimepoint::getNodeCode, t -> t));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EmcNodeDict node : dict) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nodeCode", node.getNodeCode());
            row.put("nodeName", node.getNodeName());
            row.put("seqNo", node.getSeqNo());
            row.put("targetMinutes", node.getTargetMinutes());
            EmcTimepoint tp = recorded.get(node.getNodeCode());
            if (tp != null) {
                row.put("nodeTime", tp.getNodeTime());
                long elapsed = Duration.between(visit.getStartTime(), tp.getNodeTime()).toMinutes();
                row.put("elapsedMinutes", elapsed);
                row.put("onTarget", node.getTargetMinutes() == null || elapsed <= node.getTargetMinutes());
            } else {
                row.put("nodeTime", null);
                row.put("elapsedMinutes", null);
                row.put("onTarget", null);
            }
            rows.add(row);
        }
        return rows;
    }

    /** 达标率统计（E-09）：按中心聚合病例数与节点达标 */
    public List<Map<String, Object>> stats(LocalDateTime from, LocalDateTime to) {
        List<EmcVisit> visits = visitMapper.selectList(new LambdaQueryWrapper<EmcVisit>()
                .ge(from != null, EmcVisit::getStartTime, from)
                .le(to != null, EmcVisit::getStartTime, to));
        List<EmcNodeDict> dict = nodeDictMapper.selectList(new LambdaQueryWrapper<EmcNodeDict>()
                .eq(EmcNodeDict::getStatus, 1));
        Map<Long, EmcVisit> visitMap = visits.stream()
                .collect(Collectors.toMap(EmcVisit::getId, v -> v));
        List<EmcTimepoint> allPoints = allPoints(visits);
        Map<Integer, List<EmcVisit>> byCenter = visits.stream()
                .collect(Collectors.groupingBy(EmcVisit::getCenterType));
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Integer> centerTypes = List.of(1, 2, 3, 4, 5);
        for (Integer center : centerTypes) {
            List<EmcVisit> list = byCenter.getOrDefault(center, List.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("centerType", center);
            row.put("centerName", centerName(center));
            row.put("caseCount", list.size());
            row.put("closedCount", list.stream().filter(v -> v.getStatus() == 20).count());
            List<Map<String, Object>> nodes = new ArrayList<>();
            for (EmcNodeDict node : dict.stream()
                    .filter(n -> n.getCenterType().equals(center))
                    .sorted(Comparator.comparing(EmcNodeDict::getSeqNo)).toList()) {
                long total = 0;
                long met = 0;
                for (EmcTimepoint tp : allPoints) {
                    if (!tp.getNodeCode().equals(node.getNodeCode())) {
                        continue;
                    }
                    EmcVisit v = visitMap.get(tp.getEmcVisitId());
                    if (v == null) {
                        continue;
                    }
                    total++;
                    if (node.getTargetMinutes() == null || Duration.between(
                            v.getStartTime(), tp.getNodeTime()).toMinutes() <= node.getTargetMinutes()) {
                        met++;
                    }
                }
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("nodeCode", node.getNodeCode());
                n.put("nodeName", node.getNodeName());
                n.put("targetMinutes", node.getTargetMinutes());
                n.put("metCount", met);
                n.put("totalCount", total);
                nodes.add(n);
            }
            row.put("nodes", nodes);
            rows.add(row);
        }
        return rows;
    }

    private List<EmcTimepoint> allPoints(List<EmcVisit> visits) {
        if (visits.isEmpty()) {
            return List.of();
        }
        return timepointMapper.selectList(new LambdaQueryWrapper<EmcTimepoint>()
                .in(EmcTimepoint::getEmcVisitId, visits.stream().map(EmcVisit::getId).toList()));
    }

    private EmcVisit requireVisit(Long id) {
        EmcVisit visit = visitMapper.selectById(id);
        if (visit == null) {
            throw new BizException(ErrorCode.A0001, "五大中心病例不存在");
        }
        return visit;
    }

    private String centerName(Integer center) {
        return switch (center) {
            case 1 -> "胸痛中心";
            case 2 -> "卒中中心";
            case 3 -> "创伤中心";
            case 4 -> "危重孕产妇救治中心";
            case 5 -> "危重新生儿救治中心";
            default -> "未知中心";
        };
    }

    private com.his.modules.clinic.entity.CliVisit visitMapperById(Long visitId) {
        return clinicVisitMapper.selectById(visitId);
    }
}
