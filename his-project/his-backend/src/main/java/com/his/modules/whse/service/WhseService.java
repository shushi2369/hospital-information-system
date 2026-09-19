package com.his.modules.whse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.pharmacy.service.InventoryService;
import com.his.modules.whse.dto.PurchaseOrderCreateRequest;
import com.his.modules.whse.dto.ReceiveRequest;
import com.his.modules.whse.dto.SupplierRequest;
import com.his.modules.whse.entity.BasSupplier;
import com.his.modules.whse.entity.WhsePurchaseOrder;
import com.his.modules.whse.mapper.BasSupplierMapper;
import com.his.modules.whse.mapper.WhsePurchaseOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 药库服务（S-01~S-06）：供应商、采购订单审批、到货入库（复用 FEFO 入库与流水）。
 */
@Service
@RequiredArgsConstructor
public class WhseService {
    private final WhsePurchaseOrderMapper poMapper;
    private final BasSupplierMapper supplierMapper;
    private final InventoryService inventoryService;
    private final com.his.modules.plt.service.PltService pltService;
    private final IdGenerator idGenerator;

    // ---------------- 供应商 ----------------

    @Transactional
    public Long createSupplier(SupplierRequest req) {
        Long dup = supplierMapper.selectCount(new LambdaQueryWrapper<BasSupplier>()
                .eq(BasSupplier::getSupplierCode, req.getSupplierCode()));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.B5001, "供应商编码已存在");
        }
        BasSupplier supplier = new BasSupplier();
        supplier.setSupplierCode(req.getSupplierCode());
        supplier.setSupplierName(req.getSupplierName());
        supplier.setContact(req.getContact());
        supplier.setPhone(req.getPhone());
        supplier.setStatus(1);
        supplierMapper.insert(supplier);
        return supplier.getId();
    }

    public List<BasSupplier> suppliers() {
        return supplierMapper.selectList(new LambdaQueryWrapper<BasSupplier>()
                .eq(BasSupplier::getStatus, 1).orderByAsc(BasSupplier::getId));
    }

    // ---------------- 采购订单 ----------------

    /** 创建采购单（S-03，药师）：待审批 */
    @Transactional
    public String createPo(PurchaseOrderCreateRequest req) {
        WhsePurchaseOrder po = new WhsePurchaseOrder();
        po.setPoNo(idGenerator.next("CG"));
        po.setSupplierId(req.getSupplierId());
        po.setDrugId(req.getDrugId());
        po.setQuantity(req.getQuantity());
        po.setUnitPrice(req.getUnitPrice());
        po.setExpectedDate(req.getExpectedDate());
        po.setStatus(10);
        poMapper.insert(po);
        return po.getPoNo();
    }

    /** 审批（S-04，管理员）：10 → 20 */
    @Transactional
    public void approve(Long poId) {
        WhsePurchaseOrder po = requirePo(poId);
        if (po.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "采购单不在待审批状态");
        }
        po.setStatus(20);
        po.setApproverId(CurrentUser.id());
        po.setApprovedAt(LocalDateTime.now());
        poMapper.updateById(po);
        pltService.recordEvent("whse.po.approved", po.getPoNo(), "{}");
    }

    /** 到货入库（S-05）：复用 FEFO 入库，批号=采购单号，回填入库单号 */
    @Transactional
    public String receive(Long poId, ReceiveRequest req) {
        WhsePurchaseOrder po = requirePo(poId);
        if (po.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "采购单不在已下单状态");
        }
        com.his.modules.pharmacy.dto.InboundRequest inbound = new com.his.modules.pharmacy.dto.InboundRequest();
        inbound.setDrugId(po.getDrugId());
        inbound.setBatchNo(po.getPoNo());           // 批号=采购单号，批次可追溯采购来源
        inbound.setExpiryDate(req.getExpiryDate());
        inbound.setQuantity(po.getQuantity());
        inbound.setUnitPrice(po.getUnitPrice());
        inbound.setSupplier(supplierName(po.getSupplierId()));
        String inboundNo = inventoryService.inbound(inbound);
        po.setStatus(30);
        po.setInboundNo(inboundNo);
        poMapper.updateById(po);
        pltService.recordEvent("whse.po.received", po.getPoNo(),
                "{\"inboundNo\":\"" + inboundNo + "\"}");
        return inboundNo;
    }

    /** 取消（S-06）：仅待审批/已下单且未入库可取消 */
    @Transactional
    public void cancel(Long poId) {
        WhsePurchaseOrder po = requirePo(poId);
        if (po.getStatus() == 30) {
            throw new BizException(ErrorCode.A0001, "已到货入库的采购单不能取消");
        }
        if (po.getStatus() == 40) {
            throw new BizException(ErrorCode.A0001, "采购单已取消");
        }
        po.setStatus(40);
        poMapper.updateById(po);
    }

    /** 采购单分页（S-02） */
    public PageResult<WhsePurchaseOrder> page(com.his.modules.whse.dto.PurchaseOrderQuery query) {
        Page<WhsePurchaseOrder> page = poMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<WhsePurchaseOrder>()
                        .eq(query.getStatus() != null, WhsePurchaseOrder::getStatus, query.getStatus())
                        .eq(query.getSupplierId() != null, WhsePurchaseOrder::getSupplierId, query.getSupplierId())
                        .orderByDesc(WhsePurchaseOrder::getId));
        return PageResult.of(page);
    }

    private WhsePurchaseOrder requirePo(Long poId) {
        WhsePurchaseOrder po = poMapper.selectById(poId);
        if (po == null) {
            throw new BizException(ErrorCode.A0001, "采购单不存在");
        }
        return po;
    }

    private String supplierName(Long supplierId) {
        BasSupplier supplier = supplierMapper.selectById(supplierId);
        return supplier == null ? null : supplier.getSupplierName();
    }
}
