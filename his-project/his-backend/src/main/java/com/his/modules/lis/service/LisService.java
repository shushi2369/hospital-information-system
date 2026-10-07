package com.his.modules.lis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.alert.service.AlertService;
import com.his.modules.doc.entity.DocOrder;
import com.his.modules.doc.entity.DocOrderExec;
import com.his.modules.doc.entity.DocOrderItem;
import com.his.modules.lis.dto.LisRequestQuery;
import com.his.modules.lis.dto.ResultEntryRequest;
import com.his.modules.lis.dto.ThresholdRequest;
import com.his.modules.lis.entity.LisCriticalThreshold;
import com.his.modules.lis.entity.LisReport;
import com.his.modules.lis.entity.LisRequest;
import com.his.modules.lis.entity.LisResult;
import com.his.modules.lis.entity.LisSpecimen;
import com.his.modules.lis.gateway.LabInstrumentGateway;
import com.his.modules.lis.mapper.LisCriticalThresholdMapper;
import com.his.modules.lis.mapper.LisReportMapper;
import com.his.modules.lis.mapper.LisRequestMapper;
import com.his.modules.lis.mapper.LisResultMapper;
import com.his.modules.lis.mapper.LisSpecimenMapper;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LIS 检验闭环服务：申请（护士执行检验医嘱自动生成）→ 采集 → 接收 → 结果录入（危急判定）→ 报告发布。
 */
@Service
@RequiredArgsConstructor
public class LisService {
    private final LisRequestMapper requestMapper;
    private final LisSpecimenMapper specimenMapper;
    private final LisResultMapper resultMapper;
    private final LisReportMapper reportMapper;
    private final LisCriticalThresholdMapper thresholdMapper;
    private final LabInstrumentGateway instrumentGateway;
    private final AlertService alertService;
    private final com.his.modules.plt.service.PltService pltService;
    private final com.his.modules.system.app.SystemAppService systemAppService;
    private final com.his.modules.patient.app.PatientAppService patientAppService;
    private final IdGenerator idGenerator;

    /** 护士执行检验医嘱时自动生成申请单（doc → lis 单向调用，《12》§3） */
    @Transactional
    public String createRequestFromOrder(DocOrder order, DocOrderItem item, Long executorId) {
        LisRequest request = new LisRequest();
        request.setRequestNo(idGenerator.next("JY"));
        request.setOrderId(order.getId());
        request.setAdmissionId(order.getAdmissionId());
        request.setPatientId(order.getPatientId());
        request.setDoctorId(order.getDoctorId());
        request.setSpecimenType("静脉血");
        request.setStatus(10);
        requestMapper.insert(request);
        pltService.recordEvent("lis.request.created", request.getRequestNo(),
                "{\"orderId\":" + order.getId() + "}");
        return request.getRequestNo();
    }

    /** 采集登记（L-03）：生成标本条码 */
    @Transactional
    public Map<String, Object> collect(Long requestId) {
        LisRequest request = requireRequest(requestId);
        if (request.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "申请单不在待采集状态");
        }
        LisSpecimen specimen = specimenMapper.selectOne(new LambdaQueryWrapper<LisSpecimen>()
                .eq(LisSpecimen::getRequestId, requestId).last("LIMIT 1"));
        if (specimen == null) {
            specimen = new LisSpecimen();
            specimen.setSpecimenNo(idGenerator.next("BB"));
            specimen.setRequestId(requestId);
            specimen.setStatus(1);
        }
        specimen.setCollectedAt(LocalDateTime.now());
        specimen.setCollectorId(CurrentUser.id());
        specimen.setStatus(1);
        if (specimen.getId() == null) {
            try {
                specimenMapper.insert(specimen);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // uk_specimen_req（V48）：并发采集同一申请只有一条标本
                throw new BizException(ErrorCode.A0008, "该申请已采集标本，请刷新后重试");
            }
        } else {
            specimenMapper.updateById(specimen);
        }
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<LisRequest>()
                .eq(LisRequest::getId, requestId)
                .eq(LisRequest::getStatus, 10)
                .set(LisRequest::getStatus, 20));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0008, "检验申请状态已变化，请刷新后重试");
        }
        pltService.recordEvent("lis.specimen.collected", request.getRequestNo(), "{}");
        return Map.of("requestNo", request.getRequestNo(), "specimenNo", specimen.getSpecimenNo());
    }

    /** 标本接收（L-04）：检验中 */
    @Transactional
    public void receive(Long requestId) {
        LisRequest request = requireRequest(requestId);
        if (request.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "标本未采集，不能接收");
        }
        LisSpecimen specimen = specimenMapper.selectOne(new LambdaQueryWrapper<LisSpecimen>()
                .eq(LisSpecimen::getRequestId, requestId).last("LIMIT 1"));
        if (specimen != null) {
            specimen.setStatus(2);
            specimenMapper.updateById(specimen);
        }
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<LisRequest>()
                .eq(LisRequest::getId, requestId)
                .eq(LisRequest::getStatus, 20)
                .set(LisRequest::getStatus, 30));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0008, "检验申请状态已变化，请刷新后重试");
        }
    }

    /** 结果录入（L-05）：Mock 仪器取数或手工行；危急判定 → 危急值 */
    @Transactional
    public int entry(ResultEntryRequest req) {
        LisRequest request = requireRequest(req.getRequestId());
        if (request.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "申请单不在检验中状态");
        }
        List<ResultEntryRequest.ResultRow> rows;
        String instrument = null;
        if (Boolean.TRUE.equals(req.getFetch())) {
            LisSpecimen specimen = specimenMapper.selectOne(new LambdaQueryWrapper<LisSpecimen>()
                    .eq(LisSpecimen::getRequestId, req.getRequestId()).last("LIMIT 1"));
            rows = instrumentGateway.fetchResult(specimen == null ? request.getRequestNo() : specimen.getSpecimenNo())
                    .stream().map(r -> {
                        ResultEntryRequest.ResultRow row = new ResultEntryRequest.ResultRow();
                        row.setItemName(r.itemName());
                        row.setResultValue(r.resultValue());
                        row.setUnit(r.unit());
                        row.setReferenceRange(r.referenceRange());
                        row.setAbnormalFlag(r.abnormalFlag());
                        return row;
                    }).toList();
            instrument = "Mock-1000";
        } else {
            if (req.getRows() == null || req.getRows().isEmpty()) {
                throw new BizException(ErrorCode.A0001, "结果行不能为空");
            }
            rows = req.getRows();
        }
        resultMapper.delete(new LambdaQueryWrapper<LisResult>()
                .eq(LisResult::getRequestId, req.getRequestId()));
        int criticalCount = 0;
        for (ResultEntryRequest.ResultRow row : rows) {
            LisResult result = new LisResult();
            result.setRequestId(req.getRequestId());
            result.setItemName(row.getItemName());
            result.setResultValue(row.getResultValue());
            result.setUnit(row.getUnit());
            result.setReferenceRange(row.getReferenceRange());
            int abnormal = row.getAbnormalFlag() == null ? 0 : row.getAbnormalFlag();
            int critical = judgeCritical(row.getItemName(), row.getResultValue(), abnormal);
            result.setAbnormalFlag(abnormal);
            result.setCriticalFlag(critical);
            result.setInstrument(instrument);
            result.setStatus(1);
            resultMapper.insert(result);
            if (critical == 1) {
                criticalCount++;
                alertService.create(request, result.getId(), row.getItemName(), row.getResultValue());
            }
        }
        return criticalCount;
    }

    /** 危急判定：数值型按阈值表比较 */
    private int judgeCritical(String itemName, String value, int abnormalFlag) {
        LisCriticalThreshold threshold = thresholdMapper.selectOne(new LambdaQueryWrapper<LisCriticalThreshold>()
                .eq(LisCriticalThreshold::getItemName, itemName)
                .eq(LisCriticalThreshold::getStatus, 1)
                .last("LIMIT 1"));
        if (threshold == null || threshold.getLowValue() == null && threshold.getHighValue() == null) {
            return 0;
        }
        try {
            BigDecimal v = new BigDecimal(value.trim());
            if (threshold.getHighValue() != null && v.compareTo(threshold.getHighValue()) > 0) {
                return 1;
            }
            if (threshold.getLowValue() != null && v.compareTo(threshold.getLowValue()) < 0) {
                return 1;
            }
            return 0;
        } catch (NumberFormatException e) {
            return 0; // 非数值结果不判危急
        }
    }

    /** 报告发布（L-06）：申请单 → 已报告 */
    @Transactional
    public String publish(Long requestId, Integer mutualFlag, String mutualNote) {
        LisRequest request = requireRequest(requestId);
        if (request.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "申请单不在检验中状态，不能发布");
        }
        Long exists = reportMapper.selectCount(new LambdaQueryWrapper<LisReport>()
                .eq(LisReport::getRequestId, requestId));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.A0001, "该申请已发布报告");
        }
        List<LisResult> results = resultMapper.selectList(new LambdaQueryWrapper<LisResult>()
                .eq(LisResult::getRequestId, requestId).eq(LisResult::getStatus, 1));
        String abnormal = results.stream()
                .filter(r -> r.getCriticalFlag() == 1)
                .map(LisResult::getItemName)
                .reduce((a, b) -> a + "、" + b).orElse("未见危急项");
        LisReport report = new LisReport();
        report.setReportNo(idGenerator.next("BG"));
        Long resultCount = resultMapper.selectCount(new LambdaQueryWrapper<LisResult>()
                .eq(LisResult::getRequestId, requestId).eq(LisResult::getStatus, 1));
        if (resultCount == null || resultCount == 0) {
            throw new BizException(ErrorCode.A0001, "该申请尚无检验结果，请先录入结果再发布");
        }
        report.setRequestId(requestId);
        report.setResultSummary("危急项：" + abnormal);
        report.setReporterId(CurrentUser.id());
        report.setReportTime(LocalDateTime.now());
        report.setStatus(20);
        report.setMutualFlag(mutualFlag == null ? 0 : mutualFlag);
        report.setMutualNote(mutualNote);
        try {
            reportMapper.insert(report);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.A0001, "该申请已发布报告");
        }
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<LisRequest>()
                .eq(LisRequest::getId, requestId)
                .eq(LisRequest::getStatus, 30)
                .set(LisRequest::getStatus, 40));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0008, "检验申请状态已变化，请刷新后重试");
        }
        pltService.recordEvent("lis.report.published", report.getReportNo(), "{}");
        return report.getReportNo();
    }

    /** 报告分页（L-07） */
    public PageResult<LisReport> reportPage(com.his.modules.lis.dto.LisRequestQuery query) {
        Page<LisReport> page = reportMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<LisReport>().orderByDesc(LisReport::getId));
        return PageResult.of(page);
    }

    /** 报告详情（L-08） */
    public Map<String, Object> reportDetail(Long requestId) {
        LisRequest request = requireRequest(requestId);
        LisReport report = reportMapper.selectOne(new LambdaQueryWrapper<LisReport>()
                .eq(LisReport::getRequestId, requestId).last("LIMIT 1"));
        List<LisResult> results = resultMapper.selectList(new LambdaQueryWrapper<LisResult>()
                .eq(LisResult::getRequestId, requestId).eq(LisResult::getStatus, 1));
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("request", request);
        result.put("report", report);
        result.put("results", results);
        return result;
    }

    /** 申请单分页（L-01）。开单医生名批量回填（一百轮浏览器走查：裸 ID 列） */
    public PageResult<LisRequest> page(LisRequestQuery query) {
        Page<LisRequest> page = requestMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<LisRequest>()
                        .eq(query.getAdmissionId() != null, LisRequest::getAdmissionId, query.getAdmissionId())
                        .eq(query.getPatientId() != null, LisRequest::getPatientId, query.getPatientId())
                        .eq(query.getStatus() != null, LisRequest::getStatus, query.getStatus())
                        .orderByDesc(LisRequest::getId));
        java.util.Set<Long> doctorIds = new java.util.HashSet<>();
        for (LisRequest r : page.getRecords()) {
            if (r.getDoctorId() != null) doctorIds.add(r.getDoctorId());
        }
        Map<Long, String> doctors = systemAppService.getUsernameMap(doctorIds);
        for (LisRequest r : page.getRecords()) {
            r.setDoctorName(doctors.get(r.getDoctorId()));
        }
        com.his.infrastructure.util.PatientNameBackfill.fill(page.getRecords(), patientAppService);
        return PageResult.of(page);
    }

    public LisRequest requireRequest(Long requestId) {
        LisRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new BizException(ErrorCode.A0001, "检验申请单不存在");
        }
        return request;
    }

    /** 阈值维护（L-10） */
    @Transactional
    public Long saveThreshold(ThresholdRequest req) {
        // T2+T3（一百零七轮走查）：low/high 交叉校验（low>high = 全量判危急的风暴配置）
        if (req.getLowValue() == null && req.getHighValue() == null) {
            throw new BizException(ErrorCode.A0001, "下限和上限至少填一项");
        }
        if (req.getLowValue() != null && req.getHighValue() != null
                && req.getLowValue().compareTo(req.getHighValue()) >= 0) {
            throw new BizException(ErrorCode.A0001, "下限必须小于上限");
        }
        LisCriticalThreshold threshold = thresholdMapper.selectOne(new LambdaQueryWrapper<LisCriticalThreshold>()
                .eq(LisCriticalThreshold::getItemName, req.getItemName()).last("LIMIT 1"));
        // T1（一百零七轮走查）：selectOne 按名查到的即目标行（upsert 语义安全——同名只会命中自身）
        // 改名场景由前端控制：前端编辑弹窗保存时 itemName 是主键，改名=新建不会走到这里
        if (threshold == null) {
            threshold = new LisCriticalThreshold();
            threshold.setItemName(req.getItemName());
        }
        threshold.setLowValue(req.getLowValue());
        threshold.setHighValue(req.getHighValue());
        threshold.setStatus(1);
        if (threshold.getId() == null) {
            thresholdMapper.insert(threshold);
        } else {
            thresholdMapper.updateById(threshold);
        }
        return threshold.getId();
    }

    public List<LisCriticalThreshold> thresholds() {
        return thresholdMapper.selectList(new LambdaQueryWrapper<LisCriticalThreshold>()
                .eq(LisCriticalThreshold::getStatus, 1).orderByAsc(LisCriticalThreshold::getId));
    }
}
