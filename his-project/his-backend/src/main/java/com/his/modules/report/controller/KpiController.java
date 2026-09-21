package com.his.modules.report.controller;

import com.his.common.R;
import com.his.modules.report.service.KpiService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/** 国考绩效看板接口（K-01~K-04，《19》§5.5）。只读聚合，0 新表。 */
@RestController
@RequestMapping("/api/v1/report/kpi")
@RequiredArgsConstructor
public class KpiController {
    private final KpiService kpiService;

    @GetMapping("/workload")
    @PreAuthorize("@ss.hasPerm('kpi:view')")
    public R<Map<String, Object>> workload(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return R.ok(kpiService.workload(from, to));
    }

    @GetMapping("/efficiency")
    @PreAuthorize("@ss.hasPerm('kpi:view')")
    public R<Map<String, Object>> efficiency() {
        return R.ok(kpiService.efficiency());
    }

    @GetMapping("/safety")
    @PreAuthorize("@ss.hasPerm('kpi:view')")
    public R<Map<String, Object>> safety() {
        return R.ok(kpiService.safety());
    }

    /** K-04 CSV 导出（csvCell 前导单引号防公式注入，对齐一期报表口径） */
    @GetMapping("/export")
    @PreAuthorize("@ss.hasPerm('kpi:view')")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) throws IOException {
        StringBuilder sb = new StringBuilder("指标,数值\n");
        csv(sb, "门诊人次", kpiService.workload(from, to).get("outpatientVisits"));
        csv(sb, "住院人次", kpiService.workload(from, to).get("inpatientAdmissions"));
        csv(sb, "完成手术台次", kpiService.workload(from, to).get("surgeriesCompleted"));
        Map<String, Object> eff = kpiService.efficiency();
        csv(sb, "平均住院日", eff.get("avgStayDays"));
        csv(sb, "床位使用率%", eff.get("bedUsageRate"));
        csv(sb, "次均费用", eff.get("avgBillAmount"));
        Map<String, Object> safety = kpiService.safety();
        csv(sb, "危急值闭环率%", safety.get("alertCloseRate"));
        csv(sb, "手术核查率%", safety.get("surgeryCheckRate"));
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=kpi.csv");
        // UTF-8 BOM 便于 Excel 识别
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void csv(StringBuilder sb, String name, Object value) {
        String v = String.valueOf(value);
        // 前导单引号防公式注入（值以 =+-@ 开头时）
        if (v.startsWith("=") || v.startsWith("+") || v.startsWith("-") || v.startsWith("@")) {
            v = "'" + v;
        }
        sb.append(name).append(',').append(v).append('\n');
    }
}
