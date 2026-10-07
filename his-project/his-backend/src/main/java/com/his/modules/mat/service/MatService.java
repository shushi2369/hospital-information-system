package com.his.modules.mat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.infrastructure.util.JsonEscapeUtil;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.mat.dto.*;
import com.his.modules.mat.entity.*;
import com.his.modules.mat.mapper.*;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物资耗材服务（《18》§2.1）：采购 → 审批 → 到货入库（原子累加）→ 科室领用（原子递减，不足拒绝）。
 * 库存操作对齐药库资金级安全：原子 UPDATE 带条件（防超卖/防负库存）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MatService {
    private final MatMaterialMapper materialMapper;
    private final MatStockMapper stockMapper;
    private final MatPurchaseMapper purchaseMapper;
    private final MatRequisitionMapper requisitionMapper;
    private final MatBatchMapper batchMapper;
    private final com.his.modules.whse.mapper.BasSupplierMapper supplierMapper;
    private final com.his.modules.basedata.app.BasedataAppService basedataAppService;
    private final PltService pltService;
    private final IdGenerator idGenerator;

    /** 物资字典分页（M-01 查询侧；lowStock 过滤低于安全库存） */
    public PageResult<Map<String, Object>> materialPage(MatQuery query) {
        Page<MatMaterial> page = materialMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<MatMaterial>()
                        .eq(query.getCategory() != null, MatMaterial::getCategory, query.getCategory())
                        .orderByDesc(MatMaterial::getId));
        List<Map<String, Object>> rows = page.getRecords().stream().map(m -> {
            MatStock stock = stockMapper.selectOne(new LambdaQueryWrapper<MatStock>()
                    .eq(MatStock::getMaterialId, m.getId()).last("LIMIT 1"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("materialCode", m.getMaterialCode());
            row.put("name", m.getName());
            row.put("category", m.getCategory());
            row.put("unit", m.getUnit());
            row.put("price", m.getPrice());
            row.put("safeStock", m.getSafeStock());
            row.put("quantity", stock == null ? 0 : stock.getQuantity());
            row.put("lowStock", stock == null || stock.getQuantity() < m.getSafeStock());
            return row;
        }).toList();
        Page<Map<String, Object>> mapped = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        mapped.setRecords(rows);
        return PageResult.of(mapped);
    }

    /** 物资字典创建（M-01） */
    @Transactional
    public String createMaterial(MatMaterial material) {
        if (material.getCategory() == null || material.getCategory() < 1 || material.getCategory() > 4) {
            throw new BizException(ErrorCode.A0001, "物资类别取值 1~4");
        }
        material.setId(null);
        material.setStatus(1);
        try {
            materialMapper.insert(material);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.A0001, "物资编码已存在");
        }
        // 初始化零库存行
        MatStock stock = new MatStock();
        stock.setMaterialId(material.getId());
        stock.setQuantity(0);
        stockMapper.insert(stock);
        return material.getMaterialCode();
    }

    /** 物资字典更新（M-01 PUT）：编码不可改 */
    @Transactional
    public void updateMaterial(Long id, MatMaterial req) {
        MatMaterial material = requireMaterial(id);
        material.setName(req.getName());
        if (req.getCategory() != null) {
            if (req.getCategory() < 1 || req.getCategory() > 4) {
                throw new BizException(ErrorCode.A0001, "物资类别取值 1~4");
            }
            material.setCategory(req.getCategory());
        }
        material.setUnit(req.getUnit());
        material.setPrice(req.getPrice());
        material.setSafeStock(req.getSafeStock());
        material.setStatus(req.getStatus() == null ? material.getStatus() : req.getStatus());
        if (materialMapper.updateById(material) != 1) {
            throw new BizException(ErrorCode.A0008, "物资已变化，请刷新后重试");
        }
    }

    /** 库存查询（M-02） */
    public List<MatStock> stocks() {
        return stockMapper.selectList(new LambdaQueryWrapper<MatStock>().orderByAsc(MatStock::getMaterialId));
    }

    /** 采购单分页（M-03） */
    public PageResult<MatPurchase> purchasePage(MatQuery query) {
        Page<MatPurchase> page = purchaseMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<MatPurchase>()
                        .eq(query.getStatus() != null, MatPurchase::getStatus, query.getStatus())
                        .eq(query.getMaterialId() != null, MatPurchase::getMaterialId, query.getMaterialId())
                        .orderByDesc(MatPurchase::getId));
        return PageResult.of(page);
    }

    /** 创建采购单（M-04） */
    @Transactional
    public String createPurchase(MatPurchaseRequest req) {
        MatMaterial material = requireMaterial(req.getMaterialId());
        // 一百零七轮：供应商存在性校验——原 supplierId 裸落库，99999 这类幽灵供应商照样入库
        if (req.getSupplierId() == null) {
            throw new BizException(ErrorCode.A0001, "请选择供应商");
        }
        var supplier = supplierMapper.selectById(req.getSupplierId());
        if (supplier == null || (supplier.getStatus() != null && supplier.getStatus() != 1)) {
            throw new BizException(ErrorCode.A0001, "供应商不存在或已停用");
        }
        MatPurchase po = new MatPurchase();
        po.setPoNo(idGenerator.next("MC"));
        po.setSupplierId(req.getSupplierId());
        po.setMaterialId(req.getMaterialId());
        po.setQuantity(req.getQuantity());
        po.setUnitPrice(req.getUnitPrice());
        po.setExpectedDate(req.getExpectedDate());
        po.setStatus(10);
        purchaseMapper.insert(po);
        return po.getPoNo();
    }

    /** 供应商下拉（一百零七轮：采购单裸供应商 ID 改下拉） */
    public List<com.his.modules.whse.entity.BasSupplier> supplierList() {
        return supplierMapper.selectList(new LambdaQueryWrapper<com.his.modules.whse.entity.BasSupplier>()
                .eq(com.his.modules.whse.entity.BasSupplier::getStatus, 1)
                .orderByAsc(com.his.modules.whse.entity.BasSupplier::getId));
    }

    /** 审批（M-05）：10 → 20 */
    @Transactional
    public void approve(Long id) {
        MatPurchase po = requirePurchase(id);
        if (po.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "采购单不在待审批状态");
        }
        po.setStatus(20);
        po.setApproverId(CurrentUser.id());
        po.setApprovedAt(LocalDateTime.now());
        if (purchaseMapper.updateById(po) != 1) {
            throw new BizException(ErrorCode.A0008, "采购单状态已变化，请刷新后重试");
        }
        pltService.recordEvent("mat.purchase.approved", po.getPoNo(), "{}");
    }

    /** 到货入库（M-06，四期批次化）：20 → 30，聚合累加 + 批次明细登记（FEFO 拨发依据） */
    @Transactional
    public void receive(Long id, String batchNo, java.time.LocalDate expireDate) {
        MatPurchase po = requirePurchase(id);
        if (po.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "采购单未审批或已入库");
        }
        if (expireDate == null) {
            // 一百零七轮：效期必填——原来不录效期会静默造 DEFAULT 批次（效期+1 年），FEFO 依据失真
            throw new BizException(ErrorCode.A0001, "请录入批次效期");
        }
        if (!expireDate.isAfter(java.time.LocalDate.now())) {
            throw new BizException(ErrorCode.A0001, "批次已过期，禁止入库");
        }
        po.setStatus(30);
        if (purchaseMapper.updateById(po) != 1) {
            throw new BizException(ErrorCode.A0008, "采购单状态已变化，请刷新后重试");
        }
        addStock(po.getMaterialId(), po.getQuantity());
        // 批次明细：未传批次号时以采购单号派生（效期已强制录入）
        String bn = (batchNo == null || batchNo.isBlank()) ? "PO-" + po.getPoNo() : batchNo;
        java.time.LocalDate exp = expireDate;
        MatBatch exist = batchMapper.selectOne(new LambdaQueryWrapper<MatBatch>()
                .eq(MatBatch::getMaterialId, po.getMaterialId())
                .eq(MatBatch::getBatchNo, bn).last("LIMIT 1"));
        if (exist == null) {
            MatBatch batch = new MatBatch();
            batch.setMaterialId(po.getMaterialId());
            batch.setBatchNo(bn);
            batch.setExpireDate(exp);
            batch.setQuantity(po.getQuantity());
            batch.setStatus(1);
            try {
                batchMapper.insert(batch);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new BizException(ErrorCode.A0001, "该物资同批号批次已存在");
            }
        } else {
            batchMapper.update(null, new LambdaUpdateWrapper<MatBatch>()
                    .eq(MatBatch::getId, exist.getId())
                    .setSql("quantity = quantity + " + po.getQuantity())
                    .set(MatBatch::getStatus, 1));
        }
        pltService.recordEvent("mat.purchase.received", po.getPoNo(),
                "{\"qty\":" + po.getQuantity() + ",\"batch\":\"" + JsonEscapeUtil.escape(bn) + "\"}");
    }

    /** 取消（M-07）：10/20 → 40 */
    @Transactional
    public void cancel(Long id) {
        MatPurchase po = requirePurchase(id);
        if (po.getStatus() != 10 && po.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "待审批/已下单状态才可取消");
        }
        po.setStatus(40);
        if (purchaseMapper.updateById(po) != 1) {
            throw new BizException(ErrorCode.A0008, "采购单状态已变化，请刷新后重试");
        }
    }

    /** 领用流水（M-08） */
    public PageResult<MatRequisition> requisitionPage(MatQuery query) {
        Page<MatRequisition> page = requisitionMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<MatRequisition>()
                        .eq(query.getMaterialId() != null, MatRequisition::getMaterialId, query.getMaterialId())
                        .eq(query.getDeptId() != null, MatRequisition::getDeptId, query.getDeptId())
                        .orderByDesc(MatRequisition::getId));
        // 领用科室名回填（一百零二轮裸 ID 清查 #7）
        java.util.Map<Long, ? extends com.his.modules.basedata.app.DepartmentDTO> depts =
                basedataAppService.departmentMap();
        for (MatRequisition r : page.getRecords()) {
            var d = depts.get(r.getDeptId());
            r.setDeptName(d == null ? null : d.getDeptName());
        }
        return PageResult.of(page);
    }

    /** 科室领用（M-09）：库存原子递减（quantity >= 领用量 条件更新，0 行即库存不足） */
    @Transactional
    public String requisition(MatRequisitionRequest req) {
        requireMaterial(req.getMaterialId());
        // 一百零七轮：getDepartment 对不存在 ID 返回 null——原"校验"调完即弃是空操作
        if (req.getDeptId() == null || basedataAppService.getDepartment(req.getDeptId()) == null) {
            throw new BizException(ErrorCode.A0001, "领用科室不存在");
        }
        int deducted = stockMapper.update(null, new LambdaUpdateWrapper<MatStock>()
                .eq(MatStock::getMaterialId, req.getMaterialId())
                .ge(MatStock::getQuantity, req.getQuantity())
                .setSql("quantity = quantity - " + req.getQuantity()));
        if (deducted != 1) {
            throw new BizException(ErrorCode.A0001, "库存不足，领用失败");
        }
        // 四期 FEFO：批次按效期升序拨发，跨批次拆分留痕（聚合扣减成功后批次拨发应足额；
        // 批次与聚合短暂不一致由入库同步维护，此处不足额按聚合数兜底拨尽并告警）
        String breakdown = fefoDeduct(req.getMaterialId(), req.getQuantity());
        MatRequisition req0 = new MatRequisition();
        req0.setReqNo(idGenerator.next("LY"));
        req0.setMaterialId(req.getMaterialId());
        req0.setDeptId(req.getDeptId());
        req0.setQuantity(req.getQuantity());
        req0.setApplicantId(CurrentUser.id());
        req0.setPurpose(req.getPurpose());
        req0.setStatus(1);
        req0.setBreakdown(breakdown);
        requisitionMapper.insert(req0);
        pltService.recordEvent("mat.stock.requisitioned", req0.getReqNo(),
                "{\"materialId\":" + req.getMaterialId() + ",\"qty\":" + req.getQuantity() + "}");
        return req0.getReqNo();
    }

    /** 四期 FEFO 拨发：批次按效期升序扣减，返回 breakdown JSON；不足额按剩余数兜底 */
    private String fefoDeduct(Long materialId, int qty) {
        List<MatBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<MatBatch>()
                .eq(MatBatch::getMaterialId, materialId)
                .eq(MatBatch::getStatus, 1)
                .gt(MatBatch::getQuantity, 0)
                .orderByAsc(MatBatch::getExpireDate));
        StringBuilder sb = new StringBuilder("[");
        int remain = qty;
        for (MatBatch b : batches) {
            if (remain <= 0) break;
            int take = Math.min(remain, b.getQuantity());
            int deductedBatch = batchMapper.update(null, new LambdaUpdateWrapper<MatBatch>()
                    .eq(MatBatch::getId, b.getId())
                    .ge(MatBatch::getQuantity, take)
                    .setSql("quantity = quantity - " + take));
            if (deductedBatch != 1) {
                continue; // 并发被其他请求先扣：跳过本批，由后续批次补足
            }
            if (sb.length() > 1) sb.append(",");
            sb.append("{\"batchNo\":\"").append(b.getBatchNo())
              .append("\",\"quantity\":").append(take).append("}");
            remain -= take;
        }
        if (remain > 0) {
            // 拨发缺口 = 批次与聚合漂移，须人工核对入库同步，不能静默落库
            log.warn("FEFO 批次拨发不足额: materialId={}, 申请={}, 缺口={}", materialId, qty, remain);
            pltService.recordEvent("mat.fefo.shortfall", String.valueOf(materialId),
                    "{\"shortfall\":" + remain + "}");
        }
        sb.append("]");
        return sb.toString();
    }

    /** 批次明细（M-10）：含效期预警（≤30 天） */
    public List<Map<String, Object>> batches(Long materialId) {
        List<MatBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<MatBatch>()
                .eq(materialId != null, MatBatch::getMaterialId, materialId)
                .orderByAsc(MatBatch::getExpireDate));
        java.time.LocalDate warn = java.time.LocalDate.now().plusDays(30);
        return batches.stream().map(b -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", b.getId());
            row.put("materialId", b.getMaterialId());
            row.put("batchNo", b.getBatchNo());
            row.put("expireDate", b.getExpireDate());
            row.put("quantity", b.getQuantity());
            row.put("expireSoon", !b.getExpireDate().isAfter(warn));
            return row;
        }).toList();
    }

    /** 库存原子累加（无行则初始化） */
    private void addStock(Long materialId, int qty) {
        int updated = stockMapper.update(null, new LambdaUpdateWrapper<MatStock>()
                .eq(MatStock::getMaterialId, materialId)
                .setSql("quantity = quantity + " + qty));
        if (updated != 1) {
            MatStock stock = new MatStock();
            stock.setMaterialId(materialId);
            stock.setQuantity(qty);
            try {
                stockMapper.insert(stock);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                stockMapper.update(null, new LambdaUpdateWrapper<MatStock>()
                        .eq(MatStock::getMaterialId, materialId)
                        .setSql("quantity = quantity + " + qty));
            }
        }
    }

    private MatMaterial requireMaterial(Long id) {
        MatMaterial material = materialMapper.selectById(id);
        if (material == null || material.getStatus() != 1) {
            throw new BizException(ErrorCode.A0001, "物资不存在或已停用");
        }
        return material;
    }

    private MatPurchase requirePurchase(Long id) {
        MatPurchase po = purchaseMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.A0001, "采购单不存在");
        }
        return po;
    }
}
