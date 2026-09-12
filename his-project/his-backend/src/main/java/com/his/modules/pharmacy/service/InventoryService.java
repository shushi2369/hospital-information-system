package com.his.modules.pharmacy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.DrugDTO;
import com.his.modules.pharmacy.dto.BatchQuery;
import com.his.modules.pharmacy.dto.BatchVO;
import com.his.modules.pharmacy.dto.InboundRequest;
import com.his.modules.pharmacy.dto.MovementQuery;
import com.his.modules.pharmacy.dto.WarningDTO;
import com.his.modules.pharmacy.entity.InvInboundOrder;
import com.his.modules.pharmacy.entity.InvInventoryBatch;
import com.his.modules.pharmacy.entity.InvStockMovement;
import com.his.modules.pharmacy.mapper.InvInboundOrderMapper;
import com.his.modules.pharmacy.mapper.InvInventoryBatchMapper;
import com.his.modules.pharmacy.mapper.InvStockMovementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存服务（F-08~F-11，规则《05》R13/R14）。
 */
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InvInboundOrderMapper inboundOrderMapper;
    private final InvInventoryBatchMapper batchMapper;
    private final InvStockMovementMapper movementMapper;
    private final BasedataAppService basedataAppService;
    private final IdGenerator idGenerator;

    /** 入库（F-08）：同药品同批号累加批次，写流水 */
    @Transactional
    public String inbound(InboundRequest req) {
        DrugDTO drug = basedataAppService.getDrug(req.getDrugId());
        if (drug == null) {
            throw new BizException(ErrorCode.A0001, "药品不存在");
        }
        if (!req.getExpiryDate().isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.A0001, "入库批次效期已过期，不允许入库");
        }
        String inboundNo = idGenerator.next("RK");
        InvInventoryBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<InvInventoryBatch>()
                .eq(InvInventoryBatch::getDrugId, req.getDrugId())
                .eq(InvInventoryBatch::getBatchNo, req.getBatchNo()));
        BigDecimal before;
        BigDecimal after;
        if (batch == null) {
            batch = new InvInventoryBatch();
            batch.setDrugId(req.getDrugId());
            batch.setBatchNo(req.getBatchNo());
            batch.setExpiryDate(req.getExpiryDate());
            batch.setQuantity(req.getQuantity());
            batch.setInitialQuantity(req.getQuantity());
            batch.setStatus(1);
            batchMapper.insert(batch);
            before = BigDecimal.ZERO;
            after = req.getQuantity();
        } else {
            InvInventoryBatch locked = batchMapper.selectByIdForUpdate(batch.getId());
            before = locked.getQuantity();
            after = before.add(req.getQuantity());
            locked.setQuantity(after);
            if (locked.getStatus() == 4 && after.compareTo(BigDecimal.ZERO) > 0) {
                locked.setStatus(1);
            }
            batchMapper.updateById(locked);
        }
        saveMovement(batch, 1, req.getQuantity(), before, after, 1, inboundNo);

        InvInboundOrder order = new InvInboundOrder();
        order.setInboundNo(inboundNo);
        order.setDrugId(req.getDrugId());
        order.setBatchNo(req.getBatchNo());
        order.setExpiryDate(req.getExpiryDate());
        order.setQuantity(req.getQuantity());
        order.setUnitPrice(req.getUnitPrice());
        order.setSupplier(req.getSupplier());
        order.setOperatorId(CurrentUser.id());
        order.setStatus(1);
        inboundOrderMapper.insert(order);
        return inboundNo;
    }

    /** 批次分页（F-09） */
    public PageResult<BatchVO> batchPage(BatchQuery query) {
        Page<InvInventoryBatch> page = batchMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<InvInventoryBatch>()
                        .eq(query.getDrugId() != null, InvInventoryBatch::getDrugId, query.getDrugId())
                        .like(query.getBatchNo() != null && !query.getBatchNo().isBlank(),
                                InvInventoryBatch::getBatchNo, query.getBatchNo())
                        .eq(query.getStatus() != null, InvInventoryBatch::getStatus, query.getStatus())
                        .orderByAsc(InvInventoryBatch::getExpiryDate));
        Map<Long, String> drugNames = drugNameMap(
                page.getRecords().stream().map(InvInventoryBatch::getDrugId).distinct().toList());
        return PageResult.of(page, batch -> toVO(batch, drugNames));
    }

    /** 库存预警（F-10）：可用总量 ≤ 药品预警下限（R13） */
    public List<WarningDTO> warnings() {
        List<WarningDTO> result = new ArrayList<>();
        for (DrugDTO drug : basedataAppService.listAllDrugs()) {
            List<InvInventoryBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<InvInventoryBatch>()
                    .eq(InvInventoryBatch::getDrugId, drug.getId())
                    .eq(InvInventoryBatch::getStatus, 1)
                    .orderByAsc(InvInventoryBatch::getExpiryDate));
            BigDecimal total = batches.stream().map(InvInventoryBatch::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(drug.getStockWarningQty()) <= 0) {
                WarningDTO warning = new WarningDTO();
                warning.setDrugId(drug.getId());
                warning.setDrugName(drug.getDrugName());
                warning.setTotalQuantity(total);
                warning.setStockWarningQty(drug.getStockWarningQty());
                warning.setBatches(batches.stream()
                        .map(b -> toVO(b, Map.of(drug.getId(), drug.getDrugName()))).toList());
                result.add(warning);
            }
        }
        return result;
    }

    /** 库存汇总（报表 T-05：全部启用药品的可用总量与预警标记） */
    public List<WarningDTO> inventorySummary() {
        List<WarningDTO> result = new ArrayList<>();
        for (DrugDTO drug : basedataAppService.listAllDrugs()) {
            List<InvInventoryBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<InvInventoryBatch>()
                    .eq(InvInventoryBatch::getDrugId, drug.getId())
                    .orderByAsc(InvInventoryBatch::getExpiryDate));
            BigDecimal available = batches.stream()
                    .filter(b -> b.getStatus() == 1)
                    .map(InvInventoryBatch::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            WarningDTO dto = new WarningDTO();
            dto.setDrugId(drug.getId());
            dto.setDrugName(drug.getDrugName());
            dto.setTotalQuantity(available);
            dto.setStockWarningQty(drug.getStockWarningQty());
            dto.setWarning(available.compareTo(drug.getStockWarningQty()) <= 0);
            dto.setBatches(batches.stream()
                    .map(b -> toVO(b, Map.of(drug.getId(), drug.getDrugName()))).toList());
            result.add(dto);
        }
        return result;
    }

    /** 库存流水分页（F-11） */
    public PageResult<BatchVO.Movement> movementPage(MovementQuery query) {
        Page<InvStockMovement> page = movementMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<InvStockMovement>()
                        .eq(query.getDrugId() != null, InvStockMovement::getDrugId, query.getDrugId())
                        .like(query.getRefNo() != null && !query.getRefNo().isBlank(),
                                InvStockMovement::getRefNo, query.getRefNo())
                        .orderByDesc(InvStockMovement::getId));
        Map<Long, String> drugNames = drugNameMap(
                page.getRecords().stream().map(InvStockMovement::getDrugId).distinct().toList());
        return PageResult.of(page, movement -> {
            BatchVO.Movement vo = new BatchVO.Movement();
            vo.setId(movement.getId());
            vo.setDrugId(movement.getDrugId());
            vo.setDrugName(drugNames.get(movement.getDrugId()));
            vo.setMovementType(movement.getMovementType());
            vo.setQuantity(movement.getQuantity());
            vo.setBeforeQty(movement.getBeforeQty());
            vo.setAfterQty(movement.getAfterQty());
            vo.setRefType(movement.getRefType());
            vo.setRefNo(movement.getRefNo());
            vo.setCreatedAt(movement.getCreatedAt());
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

    private Map<Long, String> drugNameMap(List<Long> drugIds) {
        Map<Long, String> names = new HashMap<>();
        for (DrugDTO drug : basedataAppService.listAllDrugs()) {
            names.put(drug.getId(), drug.getDrugName());
        }
        return names;
    }

    private BatchVO toVO(InvInventoryBatch batch, Map<Long, String> drugNames) {
        BatchVO vo = new BatchVO();
        vo.setId(batch.getId());
        vo.setDrugId(batch.getDrugId());
        vo.setDrugName(drugNames.get(batch.getDrugId()));
        vo.setBatchNo(batch.getBatchNo());
        vo.setExpiryDate(batch.getExpiryDate());
        vo.setQuantity(batch.getQuantity());
        vo.setInitialQuantity(batch.getInitialQuantity());
        vo.setStatus(batch.getStatus());
        return vo;
    }
}
