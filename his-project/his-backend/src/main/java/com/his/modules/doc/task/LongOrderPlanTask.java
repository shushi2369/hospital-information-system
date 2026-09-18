package com.his.modules.doc.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.doc.entity.DocOrder;
import com.his.modules.doc.entity.DocOrderExec;
import com.his.modules.doc.entity.DocOrderItem;
import com.his.modules.doc.mapper.DocOrderExecMapper;
import com.his.modules.doc.mapper.DocOrderItemMapper;
import com.his.modules.doc.mapper.DocOrderMapper;
import com.his.modules.inp.app.InpAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 长期医嘱执行计划生成任务（《09》§6.3 补充）：每日 00:30 为在院患者的
 * 有效长期医嘱（审核通过/执行中）生成当日执行单（按频次展开）。
 * 幂等：执行单唯一索引 (item, date, slot, type) + 先查后插。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LongOrderPlanTask {
    private static final Map<String, List<String>> FREQUENCY_SLOTS = Map.of(
            "qd", List.of("08:00"),
            "bid", List.of("08:00", "16:00"),
            "tid", List.of("08:00", "12:00", "16:00"),
            "q8h", List.of("02:00", "10:00", "18:00"),
            "prn", List.of("按需"));

    private final DocOrderMapper orderMapper;
    private final DocOrderItemMapper itemMapper;
    private final DocOrderExecMapper execMapper;
    private final InpAppService inpAppService;

    @Scheduled(cron = "0 30 0 * * ?")
    public void generateTodayPlan() {
        LocalDate today = LocalDate.now();
        List<DocOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<DocOrder>()
                .eq(DocOrder::getOrderClass, 1)
                .in(DocOrder::getStatus, 20, 30));
        int created = 0;
        for (DocOrder order : orders) {
            var admission = inpAppService.getAdmission(order.getAdmissionId());
            if (admission == null || admission.getStatus() != 10) {
                continue; // 非在院不生成（理论上出院前已停嘱，防御性跳过）
            }
            String bedNo = inpAppService.getBedNo(order.getAdmissionId());
            List<String> slots = FREQUENCY_SLOTS.getOrDefault(
                    order.getFrequency() == null ? "qd" : order.getFrequency(), List.of("08:00"));
            List<DocOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<DocOrderItem>()
                    .eq(DocOrderItem::getOrderId, order.getId())
                    .eq(DocOrderItem::getStatus, 1));
            for (DocOrderItem item : items) {
                for (String slot : slots) {
                    Long exists = execMapper.selectCount(new LambdaQueryWrapper<DocOrderExec>()
                            .eq(DocOrderExec::getItemId, item.getId())
                            .eq(DocOrderExec::getExecDate, today)
                            .eq(DocOrderExec::getExecSlot, slot)
                            .eq(DocOrderExec::getExecType, 2));
                    if (exists != null && exists > 0) {
                        continue;
                    }
                    DocOrderExec exec = new DocOrderExec();
                    exec.setOrderId(order.getId());
                    exec.setItemId(item.getId());
                    exec.setExecDate(today);
                    exec.setExecSlot(slot);
                    exec.setExecType(2);
                    exec.setBedNo(bedNo);
                    exec.setStatus(1);
                    execMapper.insert(exec);
                    created++;
                }
            }
        }
        if (created > 0) {
            log.info("长期医嘱执行计划生成: {} 条 (日期 {})", created, today);
        }
    }
}
