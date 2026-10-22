package com.his.modules.pharmacy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.infrastructure.util.LikeEscapeUtil;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.DrugDTO;
import com.his.modules.clinic.app.ClinicAppService;
import com.his.modules.clinic.app.PharmacyRxDTO;
import com.his.modules.clinic.app.RxDisplayDTO;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.pharmacy.dto.DispenseOrderQuery;
import com.his.modules.pharmacy.dto.DispenseOrderVO;
import com.his.modules.pharmacy.dto.ReturnOrderVO;
import com.his.modules.pharmacy.dto.ReturnQuery;
import com.his.modules.pharmacy.dto.ReturnRequest;
import com.his.modules.pharmacy.dto.ReviewRequest;
import com.his.modules.pharmacy.entity.PhrDispenseDetail;
import com.his.modules.pharmacy.entity.PhrDispenseOrder;
import com.his.modules.pharmacy.entity.PhrReviewRecord;
import com.his.modules.pharmacy.entity.PhrReturnDetail;
import com.his.modules.pharmacy.entity.PhrReturnOrder;
import com.his.modules.pharmacy.entity.InvInventoryBatch;
import com.his.modules.pharmacy.entity.InvStockMovement;
import com.his.modules.pharmacy.mapper.InvInventoryBatchMapper;
import com.his.modules.pharmacy.mapper.InvStockMovementMapper;
import com.his.modules.pharmacy.mapper.PhrDispenseDetailMapper;
import com.his.modules.pharmacy.mapper.PhrDispenseOrderMapper;
import com.his.modules.pharmacy.mapper.PhrReviewRecordMapper;
import com.his.modules.pharmacy.mapper.PhrReturnDetailMapper;
import com.his.modules.pharmacy.mapper.PhrReturnOrderMapper;
import com.his.modules.system.app.SystemAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 药房服务（F-01~F-07，规则《05》R7/R11/R16）。
 * 发药为原子事务：行锁处方 → 三重前置校验 → FEFO 拆批条件扣减 → 流水 → 单据，任一失败整体回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PharmacyService {
    private final ClinicAppService clinicAppService;
    private final PatientAppService patientAppService;
    private final BasedataAppService basedataAppService;
    private final SystemAppService systemAppService;
    private final PhrReviewRecordMapper reviewRecordMapper;
    private final PhrDispenseOrderMapper dispenseOrderMapper;
    private final PhrDispenseDetailMapper dispenseDetailMapper;
    private final PhrReturnOrderMapper returnOrderMapper;
    private final PhrReturnDetailMapper returnDetailMapper;
    private final InvInventoryBatchMapper batchMapper;
    private final InvStockMovementMapper movementMapper;
    private final IdGenerator idGenerator;

    /** 审核队列（F-01） */
    public List<RxDisplayDTO> reviewQueue() {
        return clinicAppService.listPrescriptionsByStatus(10, null);
    }

    /** 处方审核（F-02）：状态 10 才可审核（B4008），写审核留痕 */
    @Transactional
    public void review(Long rxId, ReviewRequest req) {
        Long reviewerId = CurrentUser.id();
        clinicAppService.reviewPrescription(rxId, req.getPass(), reviewerId, req.getComment());
        PhrReviewRecord record = new PhrReviewRecord();
        record.setPrescriptionId(rxId);
        record.setReviewerId(reviewerId);
        record.setReviewAction(Boolean.TRUE.equals(req.getPass()) ? 1 : 2);
        record.setComment(req.getComment());
        reviewRecordMapper.insert(record);
    }

    /** 可发药队列（F-03）：审核通过且已收费 */
    public List<RxDisplayDTO> dispensableQueue() {
        return clinicAppService.listDispensablePrescriptions();
    }

    /** 发药（F-04，《05》R7）：三重前置 + FEFO 拆批扣减 + 流水，失败整体回滚 */
    @Transactional
    public String dispense(Long rxId) {
        PharmacyRxDTO rx = clinicAppService.getPrescriptionForDispense(rxId);
        if (rx == null) {
            throw new BizException(ErrorCode.A0001, "处方不存在");
        }
        if (rx.getStatus() == 30) {
            throw new BizException(ErrorCode.B4003);
        }
        if (rx.getStatus() != 20) {
            throw new BizException(ErrorCode.B4001);
        }
        if (rx.getChargeStatus() == null || rx.getChargeStatus() != 1) {
            throw new BizException(ErrorCode.B4002);
        }
        Long exists = dispenseOrderMapper.selectCount(new LambdaQueryWrapper<PhrDispenseOrder>()
                .eq(PhrDispenseOrder::getPrescriptionId, rxId));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.B4003);
        }

        String dispenseNo = idGenerator.next("FY");
        BigDecimal totalQuantity = BigDecimal.ZERO;
        List<PhrDispenseDetail> details = new ArrayList<>();
        for (PharmacyRxDTO.Item item : rx.getItems()) {
            // 历史数据/绕过校验的负数量明细：直接拒绝，避免“跳过扣减仍算发药成功”
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ErrorCode.A0001, "处方明细数量异常，禁止发药：" + item.getDrugName());
            }
            BigDecimal remaining = item.getQuantity();
            for (InvInventoryBatch batch : batchMapper.selectPickable(item.getDrugId())) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                // 行锁批次并条件扣减（quantity >= 拣批量）
                InvInventoryBatch locked = batchMapper.selectByIdForUpdate(batch.getId());
                BigDecimal take = locked.getQuantity().min(remaining);
                if (take.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal before = locked.getQuantity();
                BigDecimal after = before.subtract(take);
                locked.setQuantity(after);
                if (after.compareTo(BigDecimal.ZERO) == 0) {
                    locked.setStatus(4);
                }
                batchMapper.updateById(locked);
                saveMovement(locked, 2, take.negate(), before, after, 2, dispenseNo);
                PhrDispenseDetail detail = new PhrDispenseDetail();
                detail.setPrescriptionItemId(item.getItemId());
                detail.setDrugId(item.getDrugId());
                detail.setBatchId(locked.getId());
                detail.setQuantity(take);
                detail.setStatus(1);
                details.add(detail);
                totalQuantity = totalQuantity.add(take);
                remaining = remaining.subtract(take);
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new BizException(ErrorCode.B4004, "库存不足：" + item.getDrugName() + "，缺口 " + remaining);
            }
        }

        PhrDispenseOrder order = new PhrDispenseOrder();
        order.setDispenseNo(dispenseNo);
        order.setPrescriptionId(rx.getId());
        order.setPatientId(rx.getPatientId());
        order.setDispenserId(CurrentUser.id());
        order.setTotalQuantity(totalQuantity);
        order.setDispenseTime(LocalDateTime.now());
        order.setStatus(1);
        dispenseOrderMapper.insert(order);
        for (PhrDispenseDetail detail : details) {
            detail.setDispenseOrderId(order.getId());
            dispenseDetailMapper.insert(detail);
        }
        clinicAppService.markDispensed(rxId);
        return dispenseNo;
    }

    /** 整方退药（F-06，《05》R11）：回补原批次 + 流水，处方 30→50 恢复药品费可退 */
    @Transactional
    public String returnDrug(ReturnRequest req) {
        // 行锁：并发两次退药会都通过"已退"预检，库存被回补两遍
        PhrDispenseOrder order = dispenseOrderMapper.selectOne(
                new LambdaQueryWrapper<PhrDispenseOrder>()
                        .eq(PhrDispenseOrder::getId, req.getDispenseOrderId())
                        .last("LIMIT 1 FOR UPDATE"));
        if (order == null) {
            throw new BizException(ErrorCode.A0001, "发药单不存在");
        }
        Long returned = returnOrderMapper.selectCount(new LambdaQueryWrapper<PhrReturnOrder>()
                .eq(PhrReturnOrder::getDispenseOrderId, order.getId()));
        if (returned != null && returned > 0) {
            throw new BizException(ErrorCode.B4006, "该发药单已退药，不能重复退药");
        }
        List<PhrDispenseDetail> dispenseDetails = dispenseDetailMapper.selectList(
                new LambdaQueryWrapper<PhrDispenseDetail>()
                        .eq(PhrDispenseDetail::getDispenseOrderId, order.getId()));

        String returnNo = idGenerator.next("TY");
        PhrReturnOrder returnOrder = new PhrReturnOrder();
        returnOrder.setReturnNo(returnNo);
        returnOrder.setDispenseOrderId(order.getId());
        returnOrder.setPrescriptionId(order.getPrescriptionId());
        returnOrder.setPatientId(order.getPatientId());
        returnOrder.setReason(req.getReason());
        returnOrder.setReturnTime(LocalDateTime.now());
        returnOrder.setOperatorId(CurrentUser.id());
        returnOrder.setStatus(10);
        returnOrderMapper.insert(returnOrder);

        for (PhrDispenseDetail d : dispenseDetails) {
            InvInventoryBatch batch = batchMapper.selectByIdForUpdate(d.getBatchId());
            BigDecimal before = batch.getQuantity();
            BigDecimal after = before.add(d.getQuantity());
            batch.setQuantity(after);
            if (batch.getStatus() == 4 && after.compareTo(BigDecimal.ZERO) > 0) {
                // 一百一十三轮 A-P2-1：回补要看效期——发药后已过期的批次回补为"停用过期(3)"
                // 而非"可用(1)"，否则过期库存计入可用量误导采购（selectPickable 有效期兜底不出，
                // 但 warnings/summary 只看 status，最长 24h 脏窗口）
                if (batch.getExpiryDate() != null && !batch.getExpiryDate().isAfter(java.time.LocalDate.now())) {
                    batch.setStatus(3);
                } else {
                    batch.setStatus(1);
                }
            }
            batchMapper.updateById(batch);
            saveMovement(batch, 3, d.getQuantity(), before, after, 3, returnNo);
            PhrReturnDetail rd = new PhrReturnDetail();
            rd.setReturnOrderId(returnOrder.getId());
            rd.setPrescriptionItemId(d.getPrescriptionItemId());
            rd.setDrugId(d.getDrugId());
            rd.setBatchId(batch.getId());
            rd.setQuantity(d.getQuantity());
            rd.setStatus(1);
            returnDetailMapper.insert(rd);
        }
        clinicAppService.markPrescriptionReturned(order.getPrescriptionId());
        return returnNo;
    }

    /** 发药单分页（F-05） */
    public PageResult<DispenseOrderVO> dispenseOrderPage(DispenseOrderQuery query) {
        Page<PhrDispenseOrder> page = dispenseOrderMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<PhrDispenseOrder>()
                        .like(query.getDispenseNo() != null && !query.getDispenseNo().isBlank(),
                                PhrDispenseOrder::getDispenseNo, LikeEscapeUtil.escape(query.getDispenseNo()))
                        .ge(query.getStartDate() != null, PhrDispenseOrder::getDispenseTime,
                                query.getStartDate() == null ? null : query.getStartDate().atStartOfDay())
                        .lt(query.getEndDate() != null, PhrDispenseOrder::getDispenseTime,
                                query.getEndDate() == null ? null : query.getEndDate().plusDays(1).atStartOfDay())
                        .orderByDesc(PhrDispenseOrder::getId));
        List<Long> rxIds = page.getRecords().stream().map(PhrDispenseOrder::getPrescriptionId).toList();
        // 一百一十八轮性能【2】：rxNo 一次批查（原每行 getPrescriptionView 连明细共 2 查）
        Map<Long, String> rxNoById = rxIds.isEmpty() ? new HashMap<>()
                : clinicAppService.prescriptionRxNoByIds(rxIds);
        // 一百一十八轮性能【2】：患者名批查（原每行 getById 恒 2 查——合并链+患者表）
        List<Long> ptIds = page.getRecords().stream().map(PhrDispenseOrder::getPatientId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> patientNameById = new HashMap<>();
        for (var pt : patientAppService.listByIds(ptIds)) {
            patientNameById.put(pt.getId(), pt.getName());
        }
        // 一百一十三轮 A-P3-1：批量判定已退药发药单（返回处方状态 50 = 整方退药完成）
        java.util.Set<Long> returnedRxIds = rxIds.isEmpty() ? java.util.Set.of()
                : clinicAppService.prescriptionStatusByIds(rxIds).entrySet().stream()
                        .filter(e -> e.getValue() != null && e.getValue() == 50)
                        .map(java.util.Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet());
        return PageResult.of(page, order -> {
            DispenseOrderVO vo = new DispenseOrderVO();
            vo.setId(order.getId());
            vo.setDispenseNo(order.getDispenseNo());
            vo.setPrescriptionId(order.getPrescriptionId());
            vo.setRxNo(rxNoById.get(order.getPrescriptionId()));
            vo.setPatientName(patientNameById.get(order.getPatientId()));
            vo.setDispenserName(systemAppService.getUsername(order.getDispenserId()));
            vo.setTotalQuantity(order.getTotalQuantity());
            vo.setDispenseTime(order.getDispenseTime());
            vo.setReturned(returnedRxIds.contains(order.getPrescriptionId()));
            return vo;
        });
    }

    /** 退药单分页（F-07） */
    public PageResult<ReturnOrderVO> returnPage(ReturnQuery query) {
        Page<PhrReturnOrder> page = returnOrderMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<PhrReturnOrder>()
                        .like(query.getReturnNo() != null && !query.getReturnNo().isBlank(),
                                PhrReturnOrder::getReturnNo, LikeEscapeUtil.escape(query.getReturnNo()))
                        .orderByDesc(PhrReturnOrder::getId));
        Map<Long, String> rxNoById = new HashMap<>();
        for (PhrReturnOrder order : page.getRecords()) {
            var rx = clinicAppService.getPrescriptionView(order.getPrescriptionId());
            if (rx != null) {
                rxNoById.put(order.getPrescriptionId(), rx.getRxNo());
            }
        }
        return PageResult.of(page, order -> {
            ReturnOrderVO vo = new ReturnOrderVO();
            vo.setId(order.getId());
            vo.setReturnNo(order.getReturnNo());
            vo.setRxNo(rxNoById.get(order.getPrescriptionId()));
            var dispense = dispenseOrderMapper.selectById(order.getDispenseOrderId());
            vo.setDispenseNo(dispense == null ? null : dispense.getDispenseNo());
            var patient = patientAppService.getById(order.getPatientId());
            vo.setPatientName(patient == null ? null : patient.getName());
            vo.setReason(order.getReason());
            vo.setReturnTime(order.getReturnTime());
            vo.setOperatorName(systemAppService.getUsername(order.getOperatorId()));
            return vo;
        });
    }

    private void saveMovement(InvInventoryBatch batch, int movementType, BigDecimal quantity,
                              BigDecimal before, BigDecimal after, int refType, String refNo) {
        InvStockMovement movement = new InvStockMovement();
        movement.setDrugId(batch.getDrugId());
        movement.setBatchId(batch.getId());
        movement.setMovementType(movementType);
        movement.setQuantity(quantity);
        movement.setBeforeQty(before);
        movement.setAfterQty(after);
        movement.setRefType(refType);
        movement.setRefNo(refNo);
        movementMapper.insert(movement);
    }
}
