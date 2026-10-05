package com.his.modules.doc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.ChargeItemDTO;
import com.his.modules.basedata.app.DrugDTO;
import com.his.modules.doc.dto.OrderCreateRequest;
import com.his.modules.doc.dto.OrderItemRequest;
import com.his.modules.doc.dto.ReviewOrderRequest;
import com.his.modules.doc.dto.SkinTestRequest;
import com.his.modules.doc.dto.StopOrderRequest;
import com.his.modules.doc.entity.DocOrder;
import com.his.modules.doc.entity.DocOrderExec;
import com.his.modules.doc.entity.DocOrderItem;
import com.his.modules.doc.mapper.DocOrderExecMapper;
import com.his.modules.doc.mapper.DocOrderItemMapper;
import com.his.modules.doc.mapper.DocOrderMapper;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.app.DailyFeeDTO;
import com.his.modules.pharmacy.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 医嘱闭环住院段（O-01~O-12，状态机《10》§2.3）。
 * 计费规则：非药品医嘱执行时计费；药品医嘱摆药出库时计费（来源=摆药出库）。
 */
@Service
@RequiredArgsConstructor
public class DocOrderService {
    private final DocOrderMapper orderMapper;
    private final DocOrderItemMapper itemMapper;
    private final DocOrderExecMapper execMapper;
    private final InpAppService inpAppService;
    private final BasedataAppService basedataAppService;
    private final com.his.modules.patient.app.PatientAppService patientAppService;
    private final com.his.modules.system.app.SystemAppService systemAppService;
    private final com.his.modules.pharmacy.service.InventoryService inventoryService;
    private final com.his.modules.lis.service.LisService lisAppService;
    private final com.his.modules.ris.service.RisService risAppService;
    private final com.his.modules.cdss.service.CdssService cdssAppService;
    private final com.his.modules.plt.service.PltService pltService;
    private final IdGenerator idGenerator;

    private static final Map<String, List<String>> FREQUENCY_SLOTS = Map.of(
            "qd", List.of("08:00"),
            "bid", List.of("08:00", "16:00"),
            "tid", List.of("08:00", "12:00", "16:00"),
            "q8h", List.of("02:00", "10:00", "18:00"),
            "prn", List.of("按需"));

    /** 开医嘱（O-01）：药品类需审核，非药品自动通过；生成今日执行计划 */
    @Transactional
    public String create(OrderCreateRequest req) {
        var admission = inpAppService.requireInHospital(req.getAdmissionId());
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException(ErrorCode.A0001, "医嘱明细不能为空");
        }
        boolean drug = req.getCategory() == 1;
        // 频次白名单 + 大小写归一（非法频次会静默按默认时段调度，《09》§6.3）
        if (req.getFrequency() != null) {
            req.setFrequency(req.getFrequency().toLowerCase());
        }
        if (req.getOrderClass() == 1 && !FREQUENCY_SLOTS.containsKey(req.getFrequency())) {
            throw new BizException(ErrorCode.A0001, "长期医嘱频次仅支持 qd/bid/tid/q8h/prn");
        }
        DocOrder order = new DocOrder();
        order.setOrderNo(idGenerator.next("YZ"));
        order.setAdmissionId(req.getAdmissionId());
        order.setPatientId(admission.getPatientId());
        order.setDoctorId(admission.getDoctorId());
        order.setOrderClass(req.getOrderClass());
        order.setCategory(req.getCategory());
        order.setFrequency(req.getFrequency());
        order.setStartTime(LocalDateTime.now());
        order.setSkinTestFlag(Boolean.TRUE.equals(req.getSkinTestFlag()) && drug ? 1 : 0);
        order.setStatus(drug ? 10 : 20);
        orderMapper.insert(order);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest item : req.getItems()) {
            DocOrderItem row = new DocOrderItem();
            row.setOrderId(order.getId());
            row.setStatus(1);
            if (drug) {
                DrugDTO d = basedataAppService.getDrug(item.getDrugId());
                if (d == null || d.getStatus() == 0) {
                    throw new BizException(ErrorCode.A0001, "药品不存在或已停用");
                }
                row.setDrugId(d.getId());
                row.setItemName(d.getDrugName());
                row.setSpec(d.getSpec());
                row.setUnit(d.getUnit());
                row.setUnitPrice(d.getRetailPrice());
            } else {
                ChargeItemDTO c = basedataAppService.getChargeItem(item.getChargeItemId());
                if (c == null || c.getStatus() == 0) {
                    throw new BizException(ErrorCode.A0001, "收费项目不存在或已停用");
                }
                row.setChargeItemId(c.getId());
                row.setItemName(c.getItemName());
                row.setUnit(c.getUnit());
                row.setUnitPrice(c.getPrice());
            }
            row.setDosage(item.getDosage());
            row.setFrequency(req.getFrequency());
            row.setUsageRoute(item.getUsageRoute());
            row.setDays(item.getDays());
            row.setQuantity(item.getQuantity());
            row.setAmount(row.getUnitPrice().multiply(item.getQuantity()).setScale(2, RoundingMode.HALF_UP));
            // 逐行金额上限：DECIMAL(10,2) 溢出收口（先于明细插入）
            if (row.getAmount().compareTo(new BigDecimal("99999999")) > 0) {
                throw new BizException(ErrorCode.A0001, "医嘱明细金额超出系统上限（单行 ≤ 99999999）");
            }
            row.setUsageNote(item.getUsageNote());
            itemMapper.insert(row);
            total = total.add(row.getAmount());
        }
        order.setTotalAmount(total);
        // 金额上限：DECIMAL(10,2) 溢出收口（巨量数量+单价组合）
        if (total.compareTo(new BigDecimal("99999999")) > 0) {
            throw new BizException(ErrorCode.A0001, "医嘱金额超出系统上限（单张 ≤ 99999999）");
        }
        orderMapper.updateById(order);

        // 执行计划：临时医嘱单条；长期医嘱按频次展开今日时段；皮试单独一行
        List<String> slots = req.getOrderClass() == 2
                ? List.of("立即")
                : FREQUENCY_SLOTS.getOrDefault(req.getFrequency() == null ? "qd" : req.getFrequency(), List.of("08:00"));
        if ("prn".equalsIgnoreCase(req.getFrequency())) {
            // 按需医嘱同日可多次执行：时段附加时间戳，避免唯一索引冲突
            slots = List.of("按需" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss")));
        }
        List<DocOrderItem> insertedItems = itemMapper.selectList(new LambdaQueryWrapper<DocOrderItem>()
                .eq(DocOrderItem::getOrderId, order.getId()).eq(DocOrderItem::getStatus, 1)
                .orderByAsc(DocOrderItem::getId));
        String bedNo = inpAppService.getBedNo(req.getAdmissionId());
        if (drug && order.getSkinTestFlag() == 1 && !insertedItems.isEmpty()) {
            insertExec(order.getId(), insertedItems.get(0).getId(), LocalDate.now(), "皮试", 3, bedNo);
        }
        for (DocOrderItem item : insertedItems) {
            for (String slot : slots) {
                insertExec(order.getId(), item.getId(), LocalDate.now(), slot, 2, bedNo);
            }
        }
        pltService.recordEvent("order.created", order.getOrderNo(),
                "{\"admissionId\":" + req.getAdmissionId() + ",\"category\":" + req.getCategory() + "}");
        // CDSS 后置提示（提示不阻断，REQUIRES_NEW+异常隔离，《18》§2.3）
        try {
            cdssAppService.check(order, insertedItems);
        } catch (Exception e) {
            pltService.recordEvent("cdss.check.failed", order.getOrderNo(), "{}");
        }
        return order.getOrderNo();
    }

    /** 药师审核（O-04）：药品类 10→20/70 */
    @Transactional
    public void review(Long orderId, ReviewOrderRequest req) {
        DocOrder order = requireOrder(orderId);
        if (order.getCategory() != 1) {
            throw new BizException(ErrorCode.A0001, "非药品医嘱无需药师审核");
        }
        if (order.getStatus() != 10) {
            throw new BizException(ErrorCode.B4008);
        }
        order.setStatus(Boolean.TRUE.equals(req.getPass()) ? 20 : 70);
        order.setReviewBy(CurrentUser.id());
        order.setReviewAt(LocalDateTime.now());
        order.setReviewComment(req.getComment());
        // 并发双药师审核窗口：乐观锁失败即冲突，不得静默成功
        if (orderMapper.updateById(order) != 1) {
            throw new BizException(ErrorCode.B4008, "医嘱状态已变化，请刷新后重试");
        }
        // 驳回(70)=作废路径之一：清理当日已生成的执行单，防死单永挂护士待执行列表
        if (!Boolean.TRUE.equals(req.getPass())) {
            skipFutureExec(orderId, LocalDate.now());
        }
        pltService.recordEvent("order.reviewed", order.getOrderNo(),
                "{\"pass\":" + req.getPass() + "}");
    }

    /** 摆药出库（O-08）：药品医嘱审核通过后，FEFO 扣库存 + 药品费记账 → 执行中 */
    @Transactional
    public void dispense(Long orderId) {
        DocOrder order = requireOrder(orderId);
        if (order.getCategory() != 1) {
            throw new BizException(ErrorCode.A0001, "非药品医嘱无需摆药");
        }
        if (order.getStatus() == 30 || order.getStatus() == 40) {
            throw new BizException(ErrorCode.B4003, "该医嘱已摆药/执行");
        }
        if (order.getStatus() != 20) {
            throw new BizException(ErrorCode.B6102);
        }
        // 在院校验：出院/结算后摆药会产生结算外孤儿费用与库存扣减（高危）
        inpAppService.requireInHospital(order.getAdmissionId());
        if (order.getSkinTestFlag() == 1) {
            DocOrderExec skin = execMapper.selectList(new LambdaQueryWrapper<DocOrderExec>()
                            .eq(DocOrderExec::getOrderId, orderId)
                            .eq(DocOrderExec::getExecType, 3))
                    .stream().findFirst().orElse(null);
            if (skin == null || !"阴性".equals(skin.getResult())) {
                throw new BizException(ErrorCode.B6104, "皮试未完成或未通过，禁止摆药");
            }
        }
        List<DocOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<DocOrderItem>()
                .eq(DocOrderItem::getOrderId, orderId).eq(DocOrderItem::getStatus, 1));
        for (DocOrderItem item : items) {
            inventoryService.dispenseForOrder(item.getDrugId(), item.getQuantity(), order.getOrderNo());
            // 药品费随摆药记账（来源=摆药出库）
            inpAppService.addExecFee(order.getAdmissionId(), 7, item.getItemName(),
                    item.getQuantity(), item.getUnitPrice(), item.getId());
        }
        // 条件更新断言前态：@Version 冲突 0 行静默成功 = 并发双跑双倍扣库存+双记账（八十六轮并发审计 P0）
        int dispensed = orderMapper.update(null, new LambdaUpdateWrapper<DocOrder>()
                .eq(DocOrder::getId, orderId)
                .eq(DocOrder::getStatus, 20)
                .set(DocOrder::getStatus, 30));
        if (dispensed != 1) {
            throw new BizException(ErrorCode.B6102, "医嘱状态已变化（可能已并发摆药），请刷新后重试");
        }
        pltService.recordEvent("order.dispensed", order.getOrderNo(), "{}");
    }

    /** 护士执行（O-11）：非药品执行即计费；长期/临时推进状态 */
    @Transactional
    public void execute(Long execId) {
        DocOrderExec exec = execMapper.selectById(execId);
        if (exec == null) {
            throw new BizException(ErrorCode.A0001, "执行单不存在");
        }
        if (exec.getStatus() == 2) {
            throw new BizException(ErrorCode.B6103);
        }
        DocOrder order = requireOrder(exec.getOrderId());
        validateExecutable(order);
        // 在院校验：出院/结算后执行会产生结算外孤儿费用（高危）
        inpAppService.requireInHospital(order.getAdmissionId());
        if (exec.getExecType() == 3) {
            throw new BizException(ErrorCode.A0001, "皮试结果请通过皮试登记接口录入");
        }
        // 条件更新占位执行：并发执行同一执行单仅一笔成功（防重复计费/重复申请单）
        int executed = execMapper.update(null, new LambdaUpdateWrapper<DocOrderExec>()
                .eq(DocOrderExec::getId, execId)
                .eq(DocOrderExec::getStatus, 1)
                .set(DocOrderExec::getStatus, 2)
                .set(DocOrderExec::getNurseId, CurrentUser.id())
                .set(DocOrderExec::getUpdatedAt, LocalDateTime.now()));
        if (executed != 1) {
            throw new BizException(ErrorCode.B6103);
        }
        exec.setStatus(2);
        exec.setNurseId(CurrentUser.id());
        // 非药品医嘱：执行即计费（药品费已在摆药时记账），计费单号回写执行单
        if (order.getCategory() != 1 && exec.getChargeDetailId() == null) {
            DocOrderItem item = itemMapper.selectById(exec.getItemId());
            if (item != null) {
                ChargeItemDTO c = basedataAppService.getChargeItem(item.getChargeItemId());
                Long feeId = inpAppService.addExecFee(order.getAdmissionId(),
                        c == null ? order.getCategory() : c.getCategory(),
                        item.getItemName(), item.getQuantity(), item.getUnitPrice(), exec.getId());
                exec.setChargeDetailId(feeId);
                execMapper.updateById(exec);
            }
        }
        if (order.getStatus() == 20) {
            order.setStatus(30);
            orderMapper.updateById(order);
        }
        if (order.getOrderClass() == 2 && allExecDone(order.getId())) {
            order.setStatus(40);
            orderMapper.updateById(order);
        }
        // 检验医嘱执行 → 生成 LIS 检验申请单（doc → lis 单向，《12》§3）
        if (order.getCategory() == 3 && exec.getExecType() == 2) {
            DocOrderItem lisItem = itemMapper.selectById(exec.getItemId());
            lisAppService.createRequestFromOrder(order, lisItem, CurrentUser.id());
        }
        // 检查医嘱执行 → 生成 RIS 检查申请单（doc → ris 单向，《15》§2.2，同 LIS 模式）
        if (order.getCategory() == 2 && exec.getExecType() == 2) {
            DocOrderItem risItem = itemMapper.selectById(exec.getItemId());
            risAppService.createRequestFromOrder(order, risItem, CurrentUser.id());
        }
        pltService.recordEvent("order.executed", order.getOrderNo(),
                "{\"execId\":" + execId + "}");
    }

    /** 停止长期医嘱（O-05）：未来执行计划置跳过 */
    @Transactional
    public void stop(Long orderId, StopOrderRequest req) {
        DocOrder order = requireOrder(orderId);
        if (order.getOrderClass() != 1) {
            throw new BizException(ErrorCode.A0001, "仅长期医嘱可停止");
        }
        if (order.getStatus() != 20 && order.getStatus() != 30) {
            throw new BizException(ErrorCode.B6101);
        }
        order.setStatus(50);
        order.setStopTime(LocalDateTime.now());
        order.setStopReason(req.getReason());
        if (orderMapper.updateById(order) != 1) {
            throw new BizException(ErrorCode.B6101, "医嘱状态已变化，请刷新后重试");
        }
        skipFutureExec(order.getId(), LocalDate.now());
        pltService.recordEvent("order.stopped", order.getOrderNo(), "{}");
    }

    /** 恢复长期医嘱（O-06，当日内）：重建当日被停止置为跳过的执行单 */
    @Transactional
    public void resume(Long orderId) {
        DocOrder order = requireOrder(orderId);
        if (order.getStatus() != 50) {
            throw new BizException(ErrorCode.B6101, "医嘱不在停止状态");
        }
        int resumed = orderMapper.update(null, new LambdaUpdateWrapper<DocOrder>()
                .eq(DocOrder::getId, orderId)
                .eq(DocOrder::getStatus, 50)
                .set(DocOrder::getStatus, 30)
                .set(DocOrder::getStopTime, null));
        if (resumed != 1) {
            throw new BizException(ErrorCode.B6101, "医嘱状态已变化，请刷新后重试");
        }
        LocalDate today = LocalDate.now();
        List<String> slots = FREQUENCY_SLOTS.getOrDefault(
                order.getFrequency() == null ? "qd" : order.getFrequency(), List.of("08:00"));
        for (DocOrderItem item : itemMapper.selectList(new LambdaQueryWrapper<DocOrderItem>()
                .eq(DocOrderItem::getOrderId, orderId).eq(DocOrderItem::getStatus, 1))) {
            for (String slot : slots) {
                execMapper.update(null, new LambdaUpdateWrapper<DocOrderExec>()
                        .eq(DocOrderExec::getItemId, item.getId())
                        .eq(DocOrderExec::getExecDate, today)
                        .eq(DocOrderExec::getExecSlot, slot)
                        .eq(DocOrderExec::getExecType, 2)
                        .eq(DocOrderExec::getStatus, 3)
                        .set(DocOrderExec::getStatus, 1)
                        .set(DocOrderExec::getUpdatedAt, LocalDateTime.now()));
            }
        }
    }

    /** 作废（未执行的医嘱） */
    @Transactional
    public void voidOrder(Long orderId, StopOrderRequest req) {
        DocOrder order = requireOrder(orderId);
        if (order.getStatus() == 30 || order.getStatus() == 40) {
            throw new BizException(ErrorCode.B6106, "已执行的医嘱不可作废");
        }
        int voided = orderMapper.update(null, new LambdaUpdateWrapper<DocOrder>()
                .eq(DocOrder::getId, orderId)
                .in(DocOrder::getStatus, 10, 20)
                .set(DocOrder::getStatus, 60)
                .set(DocOrder::getVoidReason, req.getReason()));
        if (voided != 1) {
            throw new BizException(ErrorCode.B6106, "已执行的医嘱不可作废");
        }
        skipFutureExec(order.getId(), LocalDate.now());
        // 检查申请联动作废（doc → ris 单向，《16》§4.2 承诺）
        risAppService.voidByOrder(orderId);
    }

    /** 皮试结果登记（O-12）：阳性 → 医嘱作废（B6104 拦截后续） */
    @Transactional
    public void skinTest(Long execId, SkinTestRequest req) {
        DocOrderExec exec = execMapper.selectById(execId);
        if (exec == null || exec.getExecType() != 3) {
            throw new BizException(ErrorCode.A0001, "皮试执行单不存在");
        }
        if (exec.getStatus() == 2) {
            throw new BizException(ErrorCode.B6103, "皮试已登记");
        }
        // 并发双登记只有一笔成功——皮试结果不能被后来者覆盖（八十六轮并发审计 P2-1）
        int registered = execMapper.update(null, new LambdaUpdateWrapper<DocOrderExec>()
                .eq(DocOrderExec::getId, execId)
                .eq(DocOrderExec::getStatus, 1)
                .set(DocOrderExec::getStatus, 2)
                .set(DocOrderExec::getNurseId, CurrentUser.id())
                .set(DocOrderExec::getResult, req.getResult()));
        if (registered != 1) {
            throw new BizException(ErrorCode.B6103, "皮试已登记");
        }
        if ("阳性".equals(req.getResult())) {
            // 仅赢家作废医嘱：条件更新兜并发（医嘱可能已被并发停止）
            orderMapper.update(null, new LambdaUpdateWrapper<DocOrder>()
                    .eq(DocOrder::getId, exec.getOrderId())
                    .in(DocOrder::getStatus, 10, 20, 30)
                    .set(DocOrder::getStatus, 60)
                    .set(DocOrder::getVoidReason, "皮试阳性，医嘱作废"));
        }
    }

    /** 护士待执行单（O-10）：当日未执行，按床号分组快照展示 */
    public List<DocOrderExec> todo(LocalDate execDate) {
        return execMapper.selectList(new LambdaQueryWrapper<DocOrderExec>()
                .eq(DocOrderExec::getExecDate, execDate)
                .eq(DocOrderExec::getStatus, 1)
                .orderByAsc(DocOrderExec::getBedNo));
    }

    /** 药师审核队列（药品类待审核） */
    public List<DocOrder> reviewQueue() {
        List<DocOrder> list = orderMapper.selectList(new LambdaQueryWrapper<DocOrder>()
                .eq(DocOrder::getCategory, 1)
                .eq(DocOrder::getStatus, 10)
                .orderByAsc(DocOrder::getId)
                .last("LIMIT 100"));
        fillDisplayNames(list);
        return list;
    }

    /** 患者姓名/医生姓名批量回填（裸实体的 ID 列在前端是"幽灵字段"恒显示 '-'，八十六轮契约审计） */
    private void fillDisplayNames(List<DocOrder> orders) {
        if (orders.isEmpty()) {
            return;
        }
        java.util.Set<Long> patientIds = new java.util.HashSet<>();
        java.util.Set<Long> doctorIds = new java.util.HashSet<>();
        for (DocOrder o : orders) {
            if (o.getPatientId() != null) {
                patientIds.add(o.getPatientId());
            }
            if (o.getDoctorId() != null) {
                doctorIds.add(o.getDoctorId());
            }
        }
        Map<Long, com.his.modules.patient.app.PatientDTO> patients = patientAppService
                .listByIds(new java.util.ArrayList<>(patientIds)).stream()
                .collect(Collectors.toMap(com.his.modules.patient.app.PatientDTO::getId, p -> p));
        Map<Long, String> doctors = systemAppService.getUsernameMap(doctorIds);
        for (DocOrder o : orders) {
            com.his.modules.patient.app.PatientDTO p = patients.get(o.getPatientId());
            o.setPatientName(p == null ? null : p.getName());
            o.setDoctorName(doctors.get(o.getDoctorId()));
        }
    }

    /** 医嘱分页 */
    public PageResult<DocOrder> page(com.his.modules.doc.dto.OrderQuery query) {
        Page<DocOrder> page = orderMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<DocOrder>()
                        .eq(query.getAdmissionId() != null, DocOrder::getAdmissionId, query.getAdmissionId())
                        .eq(query.getPatientId() != null, DocOrder::getPatientId, query.getPatientId())
                        .eq(query.getCategory() != null, DocOrder::getCategory, query.getCategory())
                        .eq(query.getStatus() != null, DocOrder::getStatus, query.getStatus())
                        .orderByDesc(DocOrder::getId));
        fillDisplayNames(page.getRecords());
        return PageResult.of(page);
    }

    /** 医嘱详情（含明细与执行记录） */
    public Map<String, Object> detail(Long orderId) {
        DocOrder order = requireOrder(orderId);
        List<DocOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<DocOrderItem>()
                .eq(DocOrderItem::getOrderId, orderId).orderByAsc(DocOrderItem::getId));
        List<DocOrderExec> execs = execMapper.selectList(new LambdaQueryWrapper<DocOrderExec>()
                .eq(DocOrderExec::getOrderId, orderId).orderByAsc(DocOrderExec::getId));
        return Map.of("order", order, "items", items, "executions", execs);
    }

    // ---------------- 内部 ----------------

    private Long firstItemId(Long orderId) {
        DocOrderItem item = itemMapper.selectList(new LambdaQueryWrapper<DocOrderItem>()
                .eq(DocOrderItem::getOrderId, orderId).eq(DocOrderItem::getStatus, 1)
                .orderByAsc(DocOrderItem::getId)).stream().findFirst().orElse(null);
        return item == null ? null : item.getId();
    }

    private Long insertExec(Long orderId, Long itemId, LocalDate date, String slot, int type, String bedNo) {
        DocOrderExec exec = new DocOrderExec();
        exec.setOrderId(orderId);
        exec.setItemId(itemId);
        exec.setExecDate(date);
        exec.setExecSlot(slot);
        exec.setExecType(type);
        exec.setBedNo(bedNo);
        exec.setStatus(1);
        execMapper.insert(exec);
        return exec.getId();
    }

    private void skipFutureExec(Long orderId, LocalDate from) {
        execMapper.update(null, new LambdaUpdateWrapper<DocOrderExec>()
                .eq(DocOrderExec::getOrderId, orderId)
                .eq(DocOrderExec::getStatus, 1)
                .ge(DocOrderExec::getExecDate, from)
                .set(DocOrderExec::getStatus, 3)
                .set(DocOrderExec::getUpdatedAt, LocalDateTime.now()));
    }

    private boolean allExecDone(Long orderId) {
        Long pending = execMapper.selectCount(new LambdaQueryWrapper<DocOrderExec>()
                .eq(DocOrderExec::getOrderId, orderId)
                .eq(DocOrderExec::getStatus, 1));
        return pending == null || pending == 0;
    }

    private void validateExecutable(DocOrder order) {
        if (order.getStatus() == 50) {
            throw new BizException(ErrorCode.B6105);
        }
        if (order.getStatus() == 60) {
            throw new BizException(ErrorCode.B6106);
        }
        if (order.getCategory() == 1 && order.getStatus() != 30) {
            // 药品医嘱必须完成审核+摆药出库（状态=执行中）方可执行
            throw new BizException(ErrorCode.B6102, "药品医嘱未审核或未摆药，不能执行");
        }
    }

    private DocOrder requireOrder(Long orderId) {
        DocOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.A0001, "医嘱不存在");
        }
        return order;
    }
}
