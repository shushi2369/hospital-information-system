package com.his.modules.plt.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.patient.app.PatientDTO;
import com.his.modules.plt.app.PltAppService;
import com.his.modules.plt.dto.IndexSearchQuery;
import com.his.modules.plt.dto.MergeRequest;
import com.his.modules.plt.entity.PltEventLog;
import com.his.modules.plt.entity.PltMasterIndex;
import com.his.modules.plt.service.PltService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台底座接口（EMPI / 集成事件）。
 */
@RestController
@RequestMapping("/api/v1/plt")
@RequiredArgsConstructor
public class PltController {
    private final PltService pltService;
    private final PltAppService pltAppService;
    private final PatientAppService patientAppService;

    @GetMapping("/index/search")
    @PreAuthorize("@ss.hasPerm('plt:index:query')")
    public R<PageResult<Map<String, Object>>> search(IndexSearchQuery query) {
        LambdaQueryWrapper<PltMasterIndex> wrapper = new LambdaQueryWrapper<PltMasterIndex>()
                .like(query.getMpiNo() != null && !query.getMpiNo().isBlank(), PltMasterIndex::getMpiNo, query.getMpiNo())
                .orderByDesc(PltMasterIndex::getId);
        Page<PltMasterIndex> page = new Page<>(Math.max(query.getPageNum(), 1),
                Math.min(Math.max(query.getPageSize(), 1), 200));
        // 姓名/患者号过滤：经患者应用服务解析出 patient_id 集合
        List<Long> matchedIds = null;
        if (query.getName() != null && !query.getName().isBlank()) {
            matchedIds = patientAppService.searchIdsByName(query.getName());
        }
        if (query.getPatientNo() != null && !query.getPatientNo().isBlank()) {
            matchedIds = patientAppService.searchIdsByPatientNo(query.getPatientNo());
        }
        if (matchedIds != null) {
            if (matchedIds.isEmpty()) {
                PageResult<Map<String, Object>> empty = new PageResult<>();
                empty.setTotal(0);
                empty.setList(List.of());
                return R.ok(empty);
            }
            wrapper.in(PltMasterIndex::getPatientId, matchedIds);
        }
        Page<PltMasterIndex> result = pltAppService.search(wrapper, page);
        Map<Long, PatientDTO> patients = new HashMap<>();
        for (PatientDTO p : patientAppService.listByIds(
                result.getRecords().stream().map(PltMasterIndex::getPatientId).distinct().toList())) {
            patients.put(p.getId(), p);
        }
        PageResult<Map<String, Object>> resp = new PageResult<>();
        resp.setTotal(result.getTotal());
        resp.setList(result.getRecords().stream().map(mpi -> {
            Map<String, Object> row = new HashMap<>();
            row.put("mpiNo", mpi.getMpiNo());
            row.put("patientId", mpi.getPatientId());
            PatientDTO p = patients.get(mpi.getPatientId());
            row.put("patientName", p == null ? null : p.getName());
            row.put("patientNo", p == null ? null : p.getPatientNo());
            row.put("mergeFlag", mpi.getMergeFlag());
            row.put("mergedInto", mpi.getMergedInto());
            return row;
        }).toList());
        return R.ok(resp);
    }

    @GetMapping("/index/{mpiNo}")
    @PreAuthorize("@ss.hasPerm('plt:index:query')")
    public R<Map<String, Object>> detail(@PathVariable String mpiNo) {
        PltMasterIndex mpi = pltAppService.getByMpiNo(mpiNo);
        if (mpi == null) {
            return R.fail(com.his.common.ErrorCode.A0001, "主索引不存在");
        }
        PatientDTO p = patientAppService.getById(mpi.getPatientId());
        Map<String, Object> data = new HashMap<>();
        data.put("mpiNo", mpi.getMpiNo());
        data.put("mergeFlag", mpi.getMergeFlag());
        data.put("mergedInto", mpi.getMergedInto());
        data.put("patient", p);
        return R.ok(data);
    }

    @PostMapping("/index/merge")
    @PreAuthorize("@ss.hasPerm('plt:index:merge')")
    @Idempotent
    @AuditLog(module = "plt", action = "患者合并", bizType = "plt_master_index")
    public R<String> merge(@Valid @RequestBody MergeRequest req) {
        return R.ok(pltService.merge(req.getSourceMpiNo(), req.getTargetMpiNo()));
    }

    @GetMapping("/events")
    @PreAuthorize("@ss.hasPerm('plt:event:query')")
    public R<List<PltEventLog>> events(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(pltAppService.listEvents(eventType, bizNo,
                startDate == null ? null : startDate.atStartOfDay(),
                endDate == null ? null : endDate.atTime(23, 59, 59)));
    }
}
