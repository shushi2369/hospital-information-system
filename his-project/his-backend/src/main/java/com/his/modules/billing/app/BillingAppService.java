package com.his.modules.billing.app;

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
        LocalDateTime e = end.atTime(23, 59, 59);
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
        LocalDateTime e = end.atTime(23, 59, 59);
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

    /** 收入明细下钻（T-04：收费明细行 + 退费单行，与收费明细同源供交叉核对） */
    public List<RevenueDetailRowDTO> revenueDetailRows(LocalDate start, LocalDate end) {
        LocalDateTime s = start.atStartOfDay();
        LocalDateTime e = end.atTime(23, 59, 59);
        List<RevenueDetailRowDTO> rows = new ArrayList<>();
        java.util.LinkedHashSet<Long> patientIds = new java.util.LinkedHashSet<>();
        for (Map<String, Object> row : chargeStatMapper.detailRows(s, e)) {
            RevenueDetailRowDTO dto = new RevenueDetailRowDTO();
            dto.setTime(toLocalDateTime(row.get("payTime")));
            dto.setDocNo((String) row.get("billNo"));
            dto.setType(1);
            Long pid = toLong(row.get("patientId"));
            dto.setPatientId(pid);
            dto.setItemName((String) row.get("itemName"));
            Object ft = row.get("feeType");
            dto.setFeeType(ft == null ? null : ((Number) ft).intValue());
            dto.setQuantity(toDecimal(row.get("quantity")));
            dto.setUnitPrice(toDecimal(row.get("unitPrice")));
            dto.setAmount(toDecimal(row.get("amount")));
            rows.add(dto);
            if (pid != null) {
                patientIds.add(pid);
            }
        }
        for (Map<String, Object> row : refundStatMapper.refundRows(s, e)) {
            RevenueDetailRowDTO dto = new RevenueDetailRowDTO();
            dto.setTime(toLocalDateTime(row.get("refundTime")));
            dto.setDocNo((String) row.get("refundNo"));
            dto.setType(2);
            Long pid = toLong(row.get("patientId"));
            dto.setPatientId(pid);
            dto.setItemName("退费：" + row.get("reason"));
            dto.setAmount(toDecimal(row.get("refundAmount")));
            rows.add(dto);
            if (pid != null) {
                patientIds.add(pid);
            }
        }
        // 患者名批量解析（一次 IN 查询）
        Map<Long, PatientDTO> patients = new HashMap<>();
        if (!patientIds.isEmpty()) {
            for (PatientDTO p : patientAppService.listByIds(new ArrayList<>(patientIds))) {
                patients.put(p.getId(), p);
            }
        }
        for (RevenueDetailRowDTO row : rows) {
            PatientDTO p = patients.get(row.getPatientId());
            row.setPatientName(p == null ? null : p.getName());
        }
        rows.sort((a, b) -> b.getTime().compareTo(a.getTime()));
        return rows;
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
