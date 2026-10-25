package com.his.modules.whse.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.DrugDTO;
import com.his.modules.pharmacy.service.InventoryService;
import com.his.modules.plt.service.PltService;
import com.his.modules.whse.dto.PurchaseOrderCreateRequest;
import com.his.modules.whse.dto.SupplierRequest;
import com.his.modules.whse.entity.BasSupplier;
import com.his.modules.whse.entity.WhsePurchaseOrder;
import com.his.modules.whse.mapper.BasSupplierMapper;
import com.his.modules.whse.mapper.WhsePurchaseOrderMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** WhseService 单测：供应商/药品幽灵校验（126 轮 R-1 P2 固化）+ 采购单状态机 + 双重入库防护。 */
class WhseServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BasSupplier.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, WhsePurchaseOrder.class);
    }

    private final WhsePurchaseOrderMapper poMapper = mock(WhsePurchaseOrderMapper.class);
    private final BasSupplierMapper supplierMapper = mock(BasSupplierMapper.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private WhseService svc() {
        return new WhseService(poMapper, supplierMapper, inventoryService,
                basedataAppService, pltService, idGenerator);
    }

    private BasSupplier supplier(int status) {
        BasSupplier s = new BasSupplier();
        s.setId(8L);
        s.setSupplierCode("SUP001");
        s.setSupplierName("国药控股");
        s.setStatus(status);
        return s;
    }

    private WhsePurchaseOrder po(int status) {
        WhsePurchaseOrder p = new WhsePurchaseOrder();
        p.setId(15L);
        p.setPoNo("CG001");
        p.setSupplierId(8L);
        p.setDrugId(20L);
        p.setQuantity(new BigDecimal("100"));
        p.setUnitPrice(new BigDecimal("3.50"));
        p.setStatus(status);
        return p;
    }

    private PurchaseOrderCreateRequest poReq() {
        PurchaseOrderCreateRequest r = new PurchaseOrderCreateRequest();
        r.setSupplierId(8L);
        r.setDrugId(20L);
        r.setQuantity(new BigDecimal("100"));
        r.setUnitPrice(new BigDecimal("3.50"));
        r.setExpectedDate(LocalDate.now().plusDays(7));
        return r;
    }

    private DrugDTO drug(int status) {
        DrugDTO d = new DrugDTO();
        d.setId(20L);
        d.setDrugName("阿莫西林");
        d.setStatus(status);
        return d;
    }

    @Test
    void createSupplier_duplicateCode_throws() {
        when(supplierMapper.selectCount(any())).thenReturn(1L);
        BizException e = assertThrows(BizException.class,
                () -> svc().createSupplier(new SupplierRequest()));
        assertEquals(ErrorCode.B5001, e.getErrorCode());
    }

    @Test
    void createSupplier_happy_defaultsEnabled() {
        when(supplierMapper.selectCount(any())).thenReturn(0L);
        SupplierRequest req = new SupplierRequest();
        req.setSupplierCode("SUP002");
        req.setSupplierName("华东医药");

        svc().createSupplier(req);

        ArgumentCaptor<BasSupplier> captor = ArgumentCaptor.forClass(BasSupplier.class);
        verify(supplierMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
        assertEquals("SUP002", captor.getValue().getSupplierCode());
    }

    @Test
    void createPo_supplierMissing_throws() {
        when(supplierMapper.selectById(8L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> svc().createPo(poReq()));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
        verify(poMapper, never()).insert(any(WhsePurchaseOrder.class));
    }

    @Test
    void createPo_supplierDisabled_throws() {
        when(supplierMapper.selectById(8L)).thenReturn(supplier(0));
        BizException e = assertThrows(BizException.class, () -> svc().createPo(poReq()));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void createPo_ghostDrug_throws() {
        // 126 轮 R-1 P2：幽灵 drugId 审批一路放行、入库时才炸——必须在创建时挡下
        when(supplierMapper.selectById(8L)).thenReturn(supplier(1));
        when(basedataAppService.getDrug(20L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> svc().createPo(poReq()));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void createPo_disabledDrug_throws() {
        when(supplierMapper.selectById(8L)).thenReturn(supplier(1));
        when(basedataAppService.getDrug(20L)).thenReturn(drug(0));
        BizException e = assertThrows(BizException.class, () -> svc().createPo(poReq()));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void createPo_happy_pendingApproval() {
        when(supplierMapper.selectById(8L)).thenReturn(supplier(1));
        when(basedataAppService.getDrug(20L)).thenReturn(drug(1));
        when(idGenerator.next("CG")).thenReturn("CG001");

        String poNo = svc().createPo(poReq());

        assertEquals("CG001", poNo);
        ArgumentCaptor<WhsePurchaseOrder> captor = ArgumentCaptor.forClass(WhsePurchaseOrder.class);
        verify(poMapper).insert(captor.capture());
        assertEquals(10, captor.getValue().getStatus());
    }

    @Test
    void approve_notPending_throws() {
        when(poMapper.selectById(15L)).thenReturn(po(20));
        BizException e = assertThrows(BizException.class, () -> svc().approve(15L));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void approve_happy_recordsApprover() {
        when(poMapper.selectById(15L)).thenReturn(po(10));
        when(poMapper.update(isNull(), any())).thenReturn(1);

        assertDoesNotThrow(() -> svc().approve(15L));
        verify(pltService).recordEvent(eq("whse.po.approved"), eq("CG001"), anyString());
    }

    @Test
    void approve_concurrentZeroRow_throws() {
        when(poMapper.selectById(15L)).thenReturn(po(10));
        when(poMapper.update(isNull(), any())).thenReturn(0);
        BizException e = assertThrows(BizException.class, () -> svc().approve(15L));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void receive_notOrdered_throws() {
        when(poMapper.selectById(15L)).thenReturn(po(10));
        BizException e = assertThrows(BizException.class,
                () -> svc().receive(15L, new com.his.modules.whse.dto.ReceiveRequest()));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
        verify(inventoryService, never()).inbound(any());
    }

    @Test
    void receive_happy_batchNoIsPoNo_withSupplierName() {
        when(poMapper.selectById(15L)).thenReturn(po(20));
        when(supplierMapper.selectById(8L)).thenReturn(supplier(1));
        when(inventoryService.inbound(any())).thenReturn("RK001");
        when(poMapper.update(isNull(), any())).thenReturn(1);

        String inboundNo = svc().receive(15L, new com.his.modules.whse.dto.ReceiveRequest());

        assertEquals("RK001", inboundNo);
        ArgumentCaptor<com.his.modules.pharmacy.dto.InboundRequest> captor =
                ArgumentCaptor.forClass(com.his.modules.pharmacy.dto.InboundRequest.class);
        verify(inventoryService).inbound(captor.capture());
        assertEquals("CG001", captor.getValue().getBatchNo());
        assertEquals("国药控股", captor.getValue().getSupplier());
        assertEquals(0, new BigDecimal("100").compareTo(captor.getValue().getQuantity()));
    }

    @Test
    void receive_concurrentZeroRow_throws_noDoubleInbound() {
        // 双重入库 = 双倍库存：条件更新 0 行必须抛错
        when(poMapper.selectById(15L)).thenReturn(po(20));
        when(supplierMapper.selectById(8L)).thenReturn(supplier(1));
        when(inventoryService.inbound(any())).thenReturn("RK001");
        when(poMapper.update(isNull(), any())).thenReturn(0);

        BizException e = assertThrows(BizException.class,
                () -> svc().receive(15L, new com.his.modules.whse.dto.ReceiveRequest()));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void cancel_received_throws() {
        when(poMapper.selectById(15L)).thenReturn(po(30));
        BizException e = assertThrows(BizException.class, () -> svc().cancel(15L));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void cancel_alreadyCancelled_throws() {
        when(poMapper.selectById(15L)).thenReturn(po(40));
        BizException e = assertThrows(BizException.class, () -> svc().cancel(15L));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void cancel_pendingOrOrdered_happy() {
        when(poMapper.selectById(15L)).thenReturn(po(10));
        when(poMapper.update(isNull(), any())).thenReturn(1);
        assertDoesNotThrow(() -> svc().cancel(15L));

        when(poMapper.selectById(15L)).thenReturn(po(20));
        when(poMapper.update(isNull(), any())).thenReturn(1);
        assertDoesNotThrow(() -> svc().cancel(15L));

        // 10→40 与 20→40 两次条件更新必须真实发生（空洞用例修复：删掉写逻辑不再绿）
        verify(poMapper, times(2)).update(isNull(), any());
    }

    @Test
    void cancel_concurrentZeroRow_throws() {
        when(poMapper.selectById(15L)).thenReturn(po(10));
        when(poMapper.update(isNull(), any())).thenReturn(0);
        BizException e = assertThrows(BizException.class, () -> svc().cancel(15L));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }
}
