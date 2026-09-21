package com.his.modules.mat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.mat.dto.*;
import com.his.modules.mat.entity.*;
import com.his.modules.mat.mapper.*;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class MatService {
    private final MatMaterialMapper materialMapper;
    private final MatStockMapper stockMapper;
    private final MatPurchaseMapper purchaseMapper;
    private final MatRequisitionMapper requisitionMapper;
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
            throw new BizException(ErrorCode.A0001, "采购单状态已变化，请刷新后重试");
        }
        pltService.recordEvent("mat.purchase.approved", po.getPoNo(), "{}");
    }

    /** 到货入库（M-06）：20 → 30，库存原子累加 */
    @Transactional
    public void receive(Long id) {
        MatPurchase po = requirePurchase(id);
        if (po.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "采购单未审批或已入库");
        }
        po.setStatus(30);
        if (purchaseMapper.updateById(po) != 1) {
            throw new BizException(ErrorCode.A0001, "采购单状态已变化，请刷新后重试");
        }
        addStock(po.getMaterialId(), po.getQuantity());
        pltService.recordEvent("mat.purchase.received", po.getPoNo(),
                "{\"qty\":" + po.getQuantity() + "}");
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
            throw new BizException(ErrorCode.A0001, "采购单状态已变化，请刷新后重试");
        }
    }

    /** 领用流水（M-08） */
    public PageResult<MatRequisition> requisitionPage(MatQuery query) {
        Page<MatRequisition> page = requisitionMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<MatRequisition>()
                        .eq(query.getMaterialId() != null, MatRequisition::getMaterialId, query.getMaterialId())
                        .eq(query.getDeptId() != null, MatRequisition::getDeptId, query.getDeptId())
                        .orderByDesc(MatRequisition::getId));
        return PageResult.of(page);
    }

    /** 科室领用（M-09）：库存原子递减（quantity >= 领用量 条件更新，0 行即库存不足） */
    @Transactional
    public String requisition(MatRequisitionRequest req) {
        requireMaterial(req.getMaterialId());
        int deducted = stockMapper.update(null, new LambdaUpdateWrapper<MatStock>()
                .eq(MatStock::getMaterialId, req.getMaterialId())
                .ge(MatStock::getQuantity, req.getQuantity())
                .setSql("quantity = quantity - " + req.getQuantity()));
        if (deducted != 1) {
            throw new BizException(ErrorCode.A0001, "库存不足，领用失败");
        }
        MatRequisition req0 = new MatRequisition();
        req0.setReqNo(idGenerator.next("LY"));
        req0.setMaterialId(req.getMaterialId());
        req0.setDeptId(req.getDeptId());
        req0.setQuantity(req.getQuantity());
        req0.setApplicantId(CurrentUser.id());
        req0.setPurpose(req.getPurpose());
        req0.setStatus(1);
        requisitionMapper.insert(req0);
        pltService.recordEvent("mat.stock.requisitioned", req0.getReqNo(),
                "{\"materialId\":" + req.getMaterialId() + ",\"qty\":" + req.getQuantity() + "}");
        return req0.getReqNo();
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
