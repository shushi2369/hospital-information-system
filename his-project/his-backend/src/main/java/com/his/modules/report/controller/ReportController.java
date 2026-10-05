package com.his.modules.report.controller;

import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.billing.app.RevenueDetailRowDTO;
import com.his.modules.report.service.ReportService;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 统计报表接口（T-01~T-05 + 收入明细 CSV 导出，只读）。
 */
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    /** 区间反转（start > end）直接拒绝：静默返回空集会让"查不到"被误读成"没有发生" */
    private void requireRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new com.his.common.BizException(com.his.common.ErrorCode.A0001, "开始日期不能晚于结束日期");
        }
    }

    @GetMapping("/registrations/daily")
    @PreAuthorize("@ss.hasPerm('report:query')")
    public R<List<com.his.modules.registration.app.DailyStatDTO>> registrationsDaily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(reportService.dailyRegistrations(startDate, endDate));
    }

    @GetMapping("/registrations/dept")
    @PreAuthorize("@ss.hasPerm('report:query')")
    public R<List<com.his.modules.registration.app.DailyStatDTO>> registrationsDept(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(reportService.deptRegistrationRanking(startDate, endDate));
    }

    @GetMapping("/visits/daily")
    @PreAuthorize("@ss.hasPerm('report:query')")
    public R<List<com.his.modules.clinic.app.VisitStatDTO>> visitsDaily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(reportService.dailyVisits(startDate, endDate));
    }

    @GetMapping("/revenue/daily")
    @PreAuthorize("@ss.hasPerm('report:query')")
    public R<List<com.his.modules.billing.app.DailyRevenueDTO>> revenueDaily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(reportService.dailyRevenue(startDate, endDate));
    }

    @GetMapping("/revenue/distribution")
    @PreAuthorize("@ss.hasPerm('report:query')")
    public R<List<com.his.modules.billing.app.DailyRevenueDTO.FeeTypeAmount>> revenueDistribution(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        requireRange(startDate, endDate);
        return R.ok(reportService.revenueDistribution(startDate, endDate));
    }

    @GetMapping("/revenue/detail")
    @PreAuthorize("@ss.hasPerm('report:query')")
    public R<PageResult<RevenueDetailRowDTO>> revenueDetail(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        requireRange(startDate, endDate);
        return R.ok(reportService.revenueDetailPage(startDate, endDate, pageNum, pageSize));
    }

    /** 收入明细导出 CSV（UTF-8 BOM，Excel 可直接打开；§8 交付物"数据导出"）。
     *  流式写出：每次拉 500 行分批查询、边查边写，长区间也不积压堆内存 */
    @GetMapping("/revenue/detail/export")
    @PreAuthorize("@ss.hasPerm('report:query')")
    public void exportRevenueDetail(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletResponse response) throws IOException {
        requireRange(startDate, endDate);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=revenue_detail.csv");
        var out = response.getOutputStream();
        // UTF-8 BOM：Excel 打开不乱码
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        out.write("时间,单号,类型,患者,项目,费用类别,数量,单价,金额\r\n".getBytes(StandardCharsets.UTF_8));
        long offset = 0;
        List<RevenueDetailRowDTO> batch;
        while (!(batch = reportService.revenueDetailBatch(startDate, endDate, offset, 500)).isEmpty()) {
            StringBuilder csv = new StringBuilder();
            for (RevenueDetailRowDTO row : batch) {
                csv.append(row.getTime() == null ? "" : fmt.format(row.getTime())).append(',')
                        .append(csvCell(row.getDocNo())).append(',')
                        .append(row.getType() != null && row.getType() == 1 ? "收费" : "退费").append(',')
                        .append(csvCell(row.getPatientName())).append(',')
                        .append(csvCell(row.getItemName())).append(',')
                        .append(row.getFeeType() == null ? "" : row.getFeeType()).append(',')
                        .append(row.getQuantity() == null ? "" : row.getQuantity()).append(',')
                        .append(row.getUnitPrice() == null ? "" : row.getUnitPrice()).append(',')
                        .append(row.getAmount() == null ? "" : row.getAmount()).append("\r\n");
            }
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            offset += batch.size();
        }
        out.flush();
        response.flushBuffer();
    }

    @GetMapping("/drug-inventory")
    @PreAuthorize("@ss.hasPerm('report:query')")
    public R<List<com.his.modules.pharmacy.dto.WarningDTO>> drugInventory() {
        return R.ok(reportService.drugInventory());
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        // CSV formula injection guard: prefix ' for =+-@ leading cells
        if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
