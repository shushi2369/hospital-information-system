package com.his.modules.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.alert.entity.AlertCritical;
import com.his.modules.alert.mapper.AlertCriticalMapper;
import com.his.modules.billing.entity.BilChargeBill;
import com.his.modules.billing.entity.BilRefundBill;
import com.his.modules.billing.mapper.BilRefundBillMapper;
import com.his.modules.billing.mapper.BilChargeBillMapper;
import com.his.modules.clinic.entity.CliVisit;
import com.his.modules.clinic.mapper.CliVisitMapper;
import com.his.modules.doc.entity.DocOrder;
import com.his.modules.doc.mapper.DocOrderMapper;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.inp.entity.InpBed;
import com.his.modules.inp.mapper.InpAdmissionMapper;
import com.his.modules.inp.mapper.InpBedMapper;
import com.his.modules.lis.entity.LisRequest;
import com.his.modules.lis.mapper.LisRequestMapper;
import com.his.modules.ors.entity.OrsCheckRecord;
import com.his.modules.ors.entity.OrsSurgeryRequest;
import com.his.modules.ors.mapper.OrsCheckRecordMapper;
import com.his.modules.ors.mapper.OrsSurgeryRequestMapper;
import com.his.modules.ris.entity.RisRequest;
import com.his.modules.ris.mapper.RisRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 国考绩效监测服务（《18》§2.4）：三类 KPI 纯只读聚合（0 新表），按时段出数。
 * 口径（院内演示口径）：危急值闭环率=已闭环/全部；手术核查率=完成手术中三张核查单齐备占比；
 * 平均住院日=已出院患者（出院-入院）均值；床位使用率=占用/总床位。
 */
@Service
@RequiredArgsConstructor
public class KpiService {
    private final CliVisitMapper visitMapper;
    private final InpAdmissionMapper admissionMapper;
    private final InpBedMapper bedMapper;
    private final BilChargeBillMapper billMapper;
    private final LisRequestMapper lisRequestMapper;
    private final RisRequestMapper risRequestMapper;
    private final OrsSurgeryRequestMapper surgeryMapper;
    private final OrsCheckRecordMapper checkMapper;
    private final AlertCriticalMapper alertMapper;
    private final com.his.modules.billing.mapper.BilRefundBillMapper refundBillMapper;
    private final DocOrderMapper orderMapper;

    /** K-01 工作量 */
    public Map<String, Object> workload(LocalDateTime from, LocalDateTime to) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outpatientVisits", visitMapper.selectCount(between(CliVisit::getCreatedAt, from, to)));
        m.put("inpatientAdmissions", admissionMapper.selectCount(between(InpAdmission::getCreatedAt, from, to)));
        m.put("surgeriesCompleted", surgeryMapper.selectCount(
                new LambdaQueryWrapper<OrsSurgeryRequest>()
                        .eq(OrsSurgeryRequest::getStatus, 60)
                        .between(from != null, OrsSurgeryRequest::getUpdatedAt, from, to == null ? LocalDateTime.now() : to)));
        m.put("labTests", lisRequestMapper.selectCount(between(LisRequest::getCreatedAt, from, to)));
        m.put("imagingExams", risRequestMapper.selectCount(between(RisRequest::getCreatedAt, from, to)));
        m.put("orders", orderMapper.selectCount(between(DocOrder::getCreatedAt, from, to)));
        return m;
    }

    /** K-02 效率（admission 支持时段过滤；床位/账单为全量现状口径） */
    public Map<String, Object> efficiency(LocalDateTime from, LocalDateTime to) {
        Map<String, Object> m = new LinkedHashMap<>();
        // 平均住院日：已出院（status>=20）且有出院时间的患者
        // 住院日口径：SQL 端聚合（TIMESTAMPDIFF），无 LIMIT 截断（二十七轮：原内存计算 LIMIT 1000 有偏差）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<InpAdmission> qw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        qw.select("COUNT(*) AS cnt", "AVG(TIMESTAMPDIFF(DAY, admission_time, discharge_time)) AS avg_days")
          .ge("status", 20).isNotNull("discharge_time")
          .ge(from != null, "discharge_time", from).le(to != null, "discharge_time", to);
        List<Map<String, Object>> agg = admissionMapper.selectMaps(qw);
        Object cnt = agg.isEmpty() ? 0 : agg.get(0).get("cnt");
        Object avgDays = agg.isEmpty() ? 0 : agg.get(0).get("avg_days");
        m.put("dischargedCount", cnt == null ? 0 : cnt);
        m.put("avgStayDays", avgDays == null ? 0
                : BigDecimal.valueOf(((Number) avgDays).doubleValue()).setScale(1, RoundingMode.HALF_UP));
        // 床位使用率：占用床/总床（inp_bed status：2 占用——以占用语义过滤）
        long total = bedMapper.selectCount(new LambdaQueryWrapper<>());
        long occupied = bedMapper.selectCount(new LambdaQueryWrapper<InpBed>().eq(InpBed::getBedStatus, 2));
        // 次均费用口径：账单原额 − 退款（bil_refund_bill），按应收净额
        BigDecimal refundTotal = refundBillMapper.selectList(new LambdaQueryWrapper<BilRefundBill>()
                .last("LIMIT 2000")).stream().map(BilRefundBill::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("bedsTotal", total);
        m.put("bedsOccupied", occupied);
        m.put("bedUsageRate", total == 0 ? 0 : BigDecimal.valueOf(occupied * 100.0 / total)
                .setScale(1, RoundingMode.HALF_UP));
        // 次均费用：账单原额口径（status 枚举 10已支付/20部分退/30全额退，无作废态；
        // 退费金额在 bil_refund_bill 另表，此处按账单原额，退款扣减登记为口径边界）
        List<BilChargeBill> bills = billMapper.selectList(new LambdaQueryWrapper<BilChargeBill>()
                .last("LIMIT 2000"));
        BigDecimal totalAmt = bills.stream().map(BilChargeBill::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("billsCount", bills.size());
        m.put("refundTotal", refundTotal);
        BigDecimal net = totalAmt.subtract(refundTotal);
        m.put("avgBillAmount", bills.isEmpty() ? 0 : net.divide(BigDecimal.valueOf(bills.size()),
                2, RoundingMode.HALF_UP));
        return m;
    }

    /** K-03 安全（支持时段过滤，默认全量） */
    public Map<String, Object> safety(LocalDateTime from, LocalDateTime to) {
        Map<String, Object> m = new LinkedHashMap<>();
        long alertTotal = alertMapper.selectCount(new LambdaQueryWrapper<AlertCritical>()
                .ge(from != null, AlertCritical::getCreatedAt, from)
                .le(to != null, AlertCritical::getCreatedAt, to));
        long alertClosed = alertMapper.selectCount(new LambdaQueryWrapper<AlertCritical>()
                .eq(AlertCritical::getStatus, 40)
                .ge(from != null, AlertCritical::getCreatedAt, from)
                .le(to != null, AlertCritical::getCreatedAt, to));
        m.put("alertsTotal", alertTotal);
        m.put("alertsClosed", alertClosed);
        m.put("alertCloseRate", alertTotal == 0 ? 0 : BigDecimal.valueOf(alertClosed * 100.0 / alertTotal)
                .setScale(1, RoundingMode.HALF_UP));
        // 手术核查率：完成手术中三张核查单齐备占比（一次 in 查询批量计数，避免 N+1）
        List<OrsSurgeryRequest> done = surgeryMapper.selectList(new LambdaQueryWrapper<OrsSurgeryRequest>()
                .eq(OrsSurgeryRequest::getStatus, 60)
                .ge(from != null, OrsSurgeryRequest::getUpdatedAt, from)
                .le(to != null, OrsSurgeryRequest::getUpdatedAt, to)
                .last("LIMIT 500"));
        long checked = 0;
        if (!done.isEmpty()) {
            List<OrsCheckRecord> checks = checkMapper.selectList(new LambdaQueryWrapper<OrsCheckRecord>()
                    .in(OrsCheckRecord::getRequestId, done.stream().map(OrsSurgeryRequest::getId).toList()));
            Map<Long, Long> byReq = checks.stream().collect(java.util.stream.Collectors
                    .groupingBy(OrsCheckRecord::getRequestId, java.util.stream.Collectors.counting()));
            checked = byReq.values().stream().filter(c -> c >= 3).count();
        }
        m.put("surgeriesDone", done.size());
        m.put("surgeriesFullyChecked", checked);
        m.put("surgeryCheckRate", done.isEmpty() ? 0 : BigDecimal.valueOf(checked * 100.0 / done.size())
                .setScale(1, RoundingMode.HALF_UP));
        return m;
    }

    private <T> com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> between(
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column,
            LocalDateTime from, LocalDateTime to) {
        return new LambdaQueryWrapper<T>()
                .ge(from != null, column, from)
                .le(to != null, column, to);
    }
}
