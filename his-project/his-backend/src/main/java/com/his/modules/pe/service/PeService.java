package com.his.modules.pe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.pe.dto.*;
import com.his.modules.pe.entity.*;
import com.his.modules.pe.mapper.*;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 体检闭环服务（《18》§2.2）：套餐 → 登记 → 分项录入（齐全校验）→ 总检发布（联动状态 40）。
 * 套餐 items JSON 存收费项目快照；分项齐全判定以套餐项目集为基准。
 */
@Service
@RequiredArgsConstructor
public class PeService {
    private final PePackageMapper packageMapper;
    private final PeRecordMapper recordMapper;
    private final PeResultMapper resultMapper;
    private final PeReportMapper reportMapper;
    private final PatientAppService patientAppService;
    private final PltService pltService;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    /** 套餐分页 */
    public PageResult<PePackage> packagePage(com.his.common.PageQuery query) {
        Page<PePackage> page = packageMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<PePackage>().orderByDesc(PePackage::getId));
        return PageResult.of(page);
    }

    /** 套餐创建（P-01） */
    @Transactional
    public String createPackage(PePackageRequest req) {
        if (req.getItems().isEmpty()) {
            throw new BizException(ErrorCode.A0001, "套餐至少包含一个项目");
        }
        PePackage pkg = new PePackage();
        pkg.setPackageNo(idGenerator.next("TC"));
        pkg.setName(req.getName());
        pkg.setPrice(req.getPrice());
        pkg.setItems(writeItems(req.getItems()));
        pkg.setStatus(1);
        packageMapper.insert(pkg);
        return pkg.getPackageNo();
    }

    /** 登记分页（P-02） */
    public PageResult<PeRecord> recordPage(PeRecordQuery query) {
        Page<PeRecord> page = recordMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<PeRecord>()
                        .eq(query.getPatientId() != null, PeRecord::getPatientId, query.getPatientId())
                        .eq(query.getStatus() != null, PeRecord::getStatus, query.getStatus())
                        .orderByAsc(PeRecord::getStatus).orderByDesc(PeRecord::getId));
        return PageResult.of(page);
    }

    /** 详情聚合（P-03）：登记 + 分项 + 报告 */
    public Map<String, Object> detail(Long id) {
        PeRecord record = requireRecord(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        PePackage pkg = packageMapper.selectById(record.getPackageId());
        result.put("package", pkg);
        List<PeResult> results = resultMapper.selectList(new LambdaQueryWrapper<PeResult>()
                .eq(PeResult::getRecordId, id));
        result.put("results", results);
        List<Map<String, Object>> pending = new ArrayList<>();
        if (pkg != null) {
            Set<Long> done = results.stream().map(PeResult::getChargeItemId).collect(Collectors.toSet());
            for (Map<String, Object> item : readItems(pkg)) {
                Object cid = item.get("chargeItemId");
                if (cid != null && !done.contains(((Number) cid).longValue())) {
                    pending.add(item);
                }
            }
        }
        result.put("pendingItems", pending);
        result.put("report", reportMapper.selectOne(new LambdaQueryWrapper<PeReport>()
                .eq(PeReport::getRecordId, id).last("LIMIT 1")));
        return result;
    }

    /** 套餐登记（P-04） */
    @Transactional
    public String register(PeRegisterRequest req) {
        patientAppService.requireActive(req.getPatientId());
        PePackage pkg = packageMapper.selectById(req.getPackageId());
        if (pkg == null || pkg.getStatus() != 1) {
            throw new BizException(ErrorCode.A0001, "套餐不存在或已停用");
        }
        PeRecord record = new PeRecord();
        record.setRecordNo(idGenerator.next("TJ"));
        record.setPatientId(req.getPatientId());
        record.setPackageId(req.getPackageId());
        record.setExamDate(req.getExamDate());
        record.setStatus(10);
        recordMapper.insert(record);
        pltService.recordEvent("pe.record.registered", record.getRecordNo(),
                "{\"packageId\":" + req.getPackageId() + "}");
        return record.getRecordNo();
    }

    /** 开始检查（P-05）：10 → 20 */
    @Transactional
    public void start(Long id) {
        PeRecord record = requireRecord(id);
        if (record.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "登记不在待检查状态");
        }
        record.setStatus(20);
        if (recordMapper.updateById(record) != 1) {
            throw new BizException(ErrorCode.A0001, "状态已变化，请刷新后重试");
        }
    }

    /** 分项结果录入（P-06，覆盖式 upsert） */
    @Transactional
    public Long saveResult(Long recordId, PeResultRequest req) {
        PeRecord record = requireRecord(recordId);
        if (record.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "体检不在检查中状态");
        }
        PeResult result = resultMapper.selectOne(new LambdaQueryWrapper<PeResult>()
                .eq(PeResult::getRecordId, recordId)
                .eq(PeResult::getChargeItemId, req.getChargeItemId()).last("LIMIT 1"));
        if (result == null) {
            result = new PeResult();
            result.setRecordId(recordId);
            result.setChargeItemId(req.getChargeItemId());
        }
        result.setItemName(req.getItemName());
        result.setResultValue(req.getResultValue());
        result.setAbnormalFlag(req.getAbnormalFlag() == null ? 0 : req.getAbnormalFlag());
        result.setExaminerId(CurrentUser.id());
        result.setNote(req.getNote());
        if (result.getId() == null) {
            resultMapper.insert(result);
        } else {
            resultMapper.updateById(result);
        }
        return result.getId();
    }

    /** 完成检查（P-07）：分项齐全（对照套餐项目集）→ 30 */
    @Transactional
    public void finish(Long id) {
        PeRecord record = requireRecord(id);
        if (record.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "体检不在检查中状态");
        }
        PePackage pkg = packageMapper.selectById(record.getPackageId());
        Set<Long> required = pkg == null ? Set.of() : readItems(pkg).stream()
                .map(m -> m.get("chargeItemId"))
                .filter(Objects::nonNull)
                .map(o -> ((Number) o).longValue())
                .collect(Collectors.toSet());
        Set<Long> done = resultMapper.selectList(new LambdaQueryWrapper<PeResult>()
                        .eq(PeResult::getRecordId, id))
                .stream().map(PeResult::getChargeItemId).collect(Collectors.toSet());
        List<Long> missing = required.stream().filter(r -> !done.contains(r)).toList();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.A0001, "分项未录齐，缺 " + missing.size() + " 项");
        }
        record.setStatus(30);
        if (recordMapper.updateById(record) != 1) {
            throw new BizException(ErrorCode.A0001, "状态已变化，请刷新后重试");
        }
    }

    /** 总检报告发布（P-08）：30 → 40 联动 */
    @Transactional
    public String publishReport(Long id, PeReportRequest req) {
        PeRecord record = requireRecord(id);
        if (record.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "体检未完成，不能发布总检");
        }
        Long exists = reportMapper.selectCount(new LambdaQueryWrapper<PeReport>()
                .eq(PeReport::getRecordId, id));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.A0001, "该体检已发布报告");
        }
        PeReport report = new PeReport();
        report.setReportNo(idGenerator.next("TJB"));
        report.setRecordId(id);
        report.setSummary(req.getSummary());
        report.setDoctorId(CurrentUser.id());
        report.setReportTime(LocalDateTime.now());
        report.setStatus(20);
        reportMapper.insert(report);
        record.setStatus(40);
        recordMapper.updateById(record);
        pltService.recordEvent("pe.report.published", report.getReportNo(), "{}");
        return report.getReportNo();
    }

    private PeRecord requireRecord(Long id) {
        PeRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException(ErrorCode.A0001, "体检登记不存在");
        }
        return record;
    }

    private String writeItems(List<PePackageRequest.PackageItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new BizException(ErrorCode.A0001, "套餐项目序列化失败");
        }
    }

    private List<Map<String, Object>> readItems(PePackage pkg) {
        try {
            return objectMapper.readValue(pkg.getItems(), new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }
}
