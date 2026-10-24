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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
        generatePlanFor(LocalDate.now());
    }

    /** 启动补偿（九十轮资源审计 P1-3）：00:30 停机窗口错过后，启动即补生成长嘱执行单
     *  （护士当日待办不缺失）。一百零七轮（日切多日补偿）：停机多日只补当日会漏中间日——
     *  改为回补最近 7 天；幂等：执行单唯一索引 + 先查后插，重复生成安全 */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        for (int i = 7; i >= 0; i--) {
            // 一百一十二轮 A12：逐日 try——某天失败不中断剩余天的补生成；补偿失败降级为"待补"，不得阻断启动
            try {
                generatePlanFor(LocalDate.now().minusDays(i));
            } catch (Exception e) {
                log.error("长嘱执行计划启动补偿失败 date={}（待下次 cron/重启补生成）", LocalDate.now().minusDays(i), e);
            }
        }
    }

    public void generatePlanFor(LocalDate today) {
        List<DocOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<DocOrder>()
                .eq(DocOrder::getOrderClass, 1)
                .in(DocOrder::getStatus, 20, 30));
        int created = 0;
        for (DocOrder order : orders) {
          // 一百一十五轮 D-08：逐单 try + uk_exec 冲突兜底（对齐 AdmissionFeeTask）——
          // 原任一单异常会让整轮中断，剩余在院患者当日执行单全部缺失
          try {
            var admission = inpAppService.getAdmission(order.getAdmissionId());
            if (admission == null || admission.getStatus() != 10) {
                continue; // 非在院不生成（理论上出院前已停嘱，防御性跳过）
            }
            // 一百一十二轮 A8：回补下限——为早于"开单日/入院日"的过去日期生成执行单
            // 会制造系统性虚假漏执行（护士照单补执行=伪造给药记录）
            java.time.LocalDate earliest = order.getCreatedAt() == null
                    ? LocalDate.MIN
                    : order.getCreatedAt().toLocalDate();
            if (admission.getAdmissionTime() != null
                    && admission.getAdmissionTime().toLocalDate().isAfter(earliest)) {
                earliest = admission.getAdmissionTime().toLocalDate();
            }
            // 药品医嘱审核通过前不生效：重启回补不得为"待审核期间"补出虚假执行单
            if (order.getCategory() != null && order.getCategory() == 1 && order.getReviewAt() != null
                    && order.getReviewAt().toLocalDate().isAfter(earliest)) {
                earliest = order.getReviewAt().toLocalDate();
            }
            if (today.isBefore(earliest)) {
                continue;
            }
            String bedNo = inpAppService.getBedNo(order.getAdmissionId());
            List<String> slots = FREQUENCY_SLOTS.getOrDefault(
                    order.getFrequency() == null ? "qd" : order.getFrequency(), List.of("08:00"));
            List<DocOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<DocOrderItem>()
                    .eq(DocOrderItem::getOrderId, order.getId())
                    .eq(DocOrderItem::getStatus, 1));
            for (DocOrderItem item : items) {
                for (String slot : slots) {
                    // 一百一十八轮性能【6】：去掉预查 selectCount（uk_exec + DuplicateKeyException
                    // 已兜底，预查冗余且每 item×slot 多 1 查）
                    // 一百一十九轮复核【1】P1：dup 捕获下沉到单条 insert——外层 catch 跳过的是
                    // "该单全部剩余 slot/明细"，部分缺口在重跑时永不补齐（ uk 拦首条即中断）
                    try {
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
                    } catch (org.springframework.dao.DuplicateKeyException e) {
                        log.info("长嘱执行单已存在，跳过 order={} slot={}", order.getId(), slot);
                    }
                }
            }
          } catch (Exception e) {
              log.error("长嘱执行单生成失败 order={}", order.getId(), e);
          }
        }
        if (created > 0) {
            log.info("长期医嘱执行计划生成: {} 条 (日期 {})", created, today);
        }
    }
}
