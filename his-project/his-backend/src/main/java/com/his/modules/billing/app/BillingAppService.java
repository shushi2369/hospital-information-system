package com.his.modules.billing.app;

import com.his.common.PageResult;
import com.his.modules.billing.mapper.ChargeStatMapper;
import com.his.modules.billing.mapper.RefundStatMapper;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.patient.app.PatientDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收费结算跨模块只读统计服务（报表模块 T-03/T-04 数据源，append-only 读取）。
 */
@Service
@RequiredArgsConstructor
public class BillingAppService {
    private final ChargeStatMapper chargeStatMapper;
    private final RefundStatMapper refundStatMapper;
    private final PatientAppService patientAppService;

    /** 日收费/退费/净额（按支付与退费时间） */
    public List<DailyRevenueDTO> dailyRevenue(LocalDate start, LocalDate end) {
        LocalDateTime s = start.atStartOfDay();
        LocalDateTime e = end.plusDays(1).atStartOfDay(); // 半开区间 [s, e)：末秒精度安全，升 DATETIME(3) 也不丢行
        Map<LocalDate, DailyRevenueDTO> byDate = new HashMap<>();
        for (Map<String, Object> row : chargeStatMapper.dailyCharge(s, e)) {
            LocalDate date = toLocalDate(row.get("date"));
            byDate.computeIfAbsent(date, this::empty).setChargeAmount(toDecimal(row.get("amount")));
        }
        for (Map<String, Object> row : refundStatMapper.dailyRefund(s, e)) {
            LocalDate date = toLocalDate(row.get("date"));
            byDate.computeIfAbsent(date, this::empty).setRefundAmount(toDecimal(row.get("amount")));
        }
        List<DailyRevenueDTO> list = new ArrayList<>(byDate.values());
        list.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        for (DailyRevenueDTO dto : list) {
            dto.setNetAmount(dto.getChargeAmount().subtract(dto.getRefundAmount()));
        }
        return list;
    }

    /** 费用类别分布（区间内收费金额，按 fee_type） */
    public List<DailyRevenueDTO.FeeTypeAmount> feeTypeDistribution(LocalDate start, LocalDate end) {
        LocalDateTime s = start.atStartOfDay();
        LocalDateTime e = end.plusDays(1).atStartOfDay(); // 半开区间 [s, e)：末秒精度安全，升 DATETIME(3) 也不丢行
        List<DailyRevenueDTO.FeeTypeAmount> list = new ArrayList<>();
        for (Map<String, Object> row : chargeStatMapper.feeTypeDistribution(s, e)) {
            DailyRevenueDTO.FeeTypeAmount dto = new DailyRevenueDTO.FeeTypeAmount();
            Object ft = row.get("feeType");
            dto.setFeeType(ft == null ? null : ((Number) ft).intValue());
            dto.setAmount(toDecimal(row.get("amount")));
            list.add(dto);
        }
        return list;
    }

    /** 收入明细下钻（T-04）：SQL 侧真分页，患者名仅对当前页批量解析 */
    public PageResult<RevenueDetailRowDTO> revenueDetailPage(LocalDate start, LocalDate end, long pageNum, long pageSize) {
        long ps = Math.min(Math.max(1, pageSize), 200);
        long pn = Math.max(1, pageNum);
        LocalDateTime s = start.atStartOfDay();
        LocalDateTime e = end.plusDays(1).atStartOfDay(); // 半开区间 [s, e)：末秒精度安全，升 DATETIME(3) 也不丢行
        long total = chargeStatMapper.detailRowsCount(s, e);
        List<RevenueDetailRowDTO> rows =
                mapDetailRows(chargeStatMapper.detailRowsPage(s, e, (pn - 1) * ps, ps));
        fillPatientNames(rows);
        PageResult<RevenueDetailRowDTO> page = new PageResult<>();
        page.setTotal(total);
        page.setList(rows);
        return page;
    }

    /** 导出分批拉取：每次固定行数，调用方循环到空批为止（流式写响应，不积压堆内存） */
    public List<RevenueDetailRowDTO> revenueDetailBatch(LocalDate start, LocalDate end, long offset, int limit) {
        int capped = Math.min(Math.max(1, limit), 1000);
        LocalDateTime s = start.atStartOfDay();
        LocalDateTime e = end.plusDays(1).atStartOfDay(); // 半开区间 [s, e)：末秒精度安全，升 DATETIME(3) 也不丢行
        List<RevenueDetailRowDTO> rows =
                mapDetailRows(chargeStatMapper.detailRowsPage(s, e, Math.max(0, offset), capped));
        fillPatientNames(rows);
        return rows;
    }

    private List<RevenueDetailRowDTO> mapDetailRows(List<Map<String, Object>> raw) {
        List<RevenueDetailRowDTO> rows = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            RevenueDetailRowDTO dto = new RevenueDetailRowDTO();
            dto.setTime(toLocalDateTime(row.get("payTime")));
            dto.setDocNo((String) row.get("billNo"));
            dto.setType(row.get("feeType") != null ? 1 : 2);
            Long pid = toLong(row.get("patientId"));
            dto.setPatientId(pid);
            dto.setItemName((String) row.get("itemName"));
            Object ft = row.get("feeType");
            dto.setFeeType(ft == null ? null : ((Number) ft).intValue());
            dto.setQuantity(toDecimal(row.get("quantity")));
            dto.setUnitPrice(toDecimal(row.get("unitPrice")));
            dto.setAmount(toDecimal(row.get("amount")));
            rows.add(dto);
        }
        return rows;
    }

    private void fillPatientNames(List<RevenueDetailRowDTO> rows) {
        java.util.LinkedHashSet<Long> patientIds = new java.util.LinkedHashSet<>();
        for (RevenueDetailRowDTO row : rows) {
            if (row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
        }
        if (patientIds.isEmpty()) {
            return;
        }
        Map<Long, PatientDTO> patients = new HashMap<>();
        for (PatientDTO p : patientAppService.listByIds(new ArrayList<>(patientIds))) {
            patients.put(p.getId(), p);
        }
        for (RevenueDetailRowDTO row : rows) {
            PatientDTO p = patients.get(row.getPatientId());
            row.setPatientName(p == null ? null : p.getName());
        }
    }

    private DailyRevenueDTO empty(LocalDate date) {
        DailyRevenueDTO dto = new DailyRevenueDTO();
        dto.setDate(date);
        dto.setChargeAmount(BigDecimal.ZERO);
        dto.setRefundAmount(BigDecimal.ZERO);
        dto.setNetAmount(BigDecimal.ZERO);
        return dto;
    }

    private BigDecimal toDecimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof LocalDateTime d) {
            return d.toLocalDate();
        }
        if (value instanceof Date d) {
            return new java.sql.Date(d.getTime()).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime d) {
            return d;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof Date d) {
            return new java.sql.Timestamp(d.getTime()).toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T").substring(0, 19));
    }
}
