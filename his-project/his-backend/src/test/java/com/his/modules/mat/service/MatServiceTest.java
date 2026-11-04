package com.his.modules.mat.service;

import com.his.common.BizException;
import com.his.modules.mat.dto.MatRequisitionRequest;
import com.his.modules.mat.entity.MatMaterial;
import com.his.modules.mat.entity.MatBatch;
import com.his.modules.mat.entity.MatPurchase;
import com.his.modules.mat.entity.MatRequisition;
import com.his.modules.mat.entity.MatStock;
import com.his.modules.mat.mapper.MatBatchMapper;
import com.his.modules.mat.mapper.MatMaterialMapper;
import com.his.modules.mat.mapper.MatPurchaseMapper;
import com.his.modules.mat.mapper.MatRequisitionMapper;
import com.his.modules.mat.mapper.MatStockMapper;
import com.his.modules.plt.service.PltService;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MatService 核心逻辑单测：状态机 + 校验 + 原子扣减。 */
class MatServiceTest extends com.his.UnitTestBase {

    private final MatMaterialMapper materialMapper = mock(MatMaterialMapper.class);
    private final MatStockMapper stockMapper = mock(MatStockMapper.class);
    private final MatPurchaseMapper purchaseMapper = mock(MatPurchaseMapper.class);
    private final MatRequisitionMapper requisitionMapper = mock(MatRequisitionMapper.class);
    private final MatBatchMapper batchMapper = mock(MatBatchMapper.class);
    private final com.his.modules.whse.mapper.BasSupplierMapper supplierMapper =
            mock(com.his.modules.whse.mapper.BasSupplierMapper.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private final MatService service = new MatService(
            materialMapper, stockMapper, purchaseMapper, requisitionMapper,
            batchMapper, supplierMapper, basedataAppService, pltService, idGenerator);

    private MatMaterial material(Long id) {
        MatMaterial m = new MatMaterial();
        m.setId(id); m.setMaterialCode("MAT"+id); m.setName("物资"+id);
        m.setCategory(1); m.setUnit("支"); m.setPrice(BigDecimal.ONE); m.setSafeStock(0); m.setStatus(1);
        return m;
    }

    private com.his.modules.mat.dto.MatRequisitionRequest req(Long matId, int qty) {
        var r = new com.his.modules.mat.dto.MatRequisitionRequest();
        r.setMaterialId(matId); r.setDeptId(1L); r.setQuantity(qty); r.setPurpose("test");
        return r;
    }

    @Test
    void requisition_rejectsNullMaterial() {
        when(materialMapper.selectById(99L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.requisition(req(99L, 1)));
        assertTrue(e.getMessage().contains("不存在"));
    }

    @Test
    void requisition_rejectsInsufficientStock() {
        when(materialMapper.selectById(1L)).thenReturn(material(1L));
        when(basedataAppService.getDepartment(1L))
                .thenReturn(new com.his.modules.basedata.app.DepartmentDTO());
        BizException e = assertThrows(BizException.class, () -> service.requisition(req(1L, 1)));
        assertTrue(e.getMessage().contains("库存不足"));
    }

    /** 扣减守卫必须由数据库重算过期量：两笔并发领用都能通过 Java 预检，只有条件更新能兜住 */
    @Test
    void requisition_usesExpiredAwareAtomicDeduction() {
        when(materialMapper.selectById(1L)).thenReturn(material(1L));
        when(basedataAppService.getDepartment(1L))
                .thenReturn(new com.his.modules.basedata.app.DepartmentDTO());
        MatStock stock = new MatStock();
        stock.setMaterialId(1L);
        stock.setQuantity(5);
        when(stockMapper.selectOne(any())).thenReturn(stock);
        when(batchMapper.selectList(any())).thenReturn(java.util.List.of());
        when(stockMapper.deductAvailable(1L, 3)).thenReturn(0);

        BizException e = assertThrows(BizException.class, () -> service.requisition(req(1L, 3)));
        assertTrue(e.getMessage().contains("可用库存不足"));
        verify(stockMapper).deductAvailable(1L, 3);
    }

    @Test
    void requisition_succeedsAfterAtomicDeduction() {
        when(materialMapper.selectById(1L)).thenReturn(material(1L));
        when(basedataAppService.getDepartment(1L))
                .thenReturn(new com.his.modules.basedata.app.DepartmentDTO());
        MatStock stock = new MatStock();
        stock.setMaterialId(1L);
        stock.setQuantity(5);
        when(stockMapper.selectOne(any())).thenReturn(stock);
        when(batchMapper.selectList(any())).thenReturn(java.util.List.of());
        when(stockMapper.deductAvailable(1L, 3)).thenReturn(1);
        when(idGenerator.next("LY")).thenReturn("LY0001");

        assertEquals("LY0001", service.requisition(req(1L, 3)));
        verify(requisitionMapper).insert(any(com.his.modules.mat.entity.MatRequisition.class));
    }

    /** 批号经入库录入后必须原样留在合法 JSON 中，否则领用单无法写入 JSON 类型的拆批列。 */
    @ParameterizedTest
    @ValueSource(strings = {"批次正常", "批次\"引号", "批次\\反斜杠", "批次\n换行"})
    void requisition_preservesBatchNumberInValidBreakdownJson(String batchNo) throws Exception {
        when(materialMapper.selectById(1L)).thenReturn(material(1L));
        when(basedataAppService.getDepartment(1L))
                .thenReturn(new com.his.modules.basedata.app.DepartmentDTO());
        MatStock stock = new MatStock();
        stock.setMaterialId(1L);
        stock.setQuantity(5);
        when(stockMapper.selectOne(any())).thenReturn(stock);
        MatBatch batch = new MatBatch();
        batch.setId(2L);
        batch.setMaterialId(1L);
        batch.setBatchNo(batchNo);
        batch.setQuantity(5);
        batch.setStatus(1);
        batch.setExpireDate(java.time.LocalDate.now().plusDays(30));
        when(batchMapper.selectList(any())).thenReturn(java.util.List.of(), java.util.List.of(batch));
        when(stockMapper.deductAvailable(1L, 3)).thenReturn(1);
        when(batchMapper.update(any(), any())).thenReturn(1);
        when(idGenerator.next("LY")).thenReturn("LY0001");

        assertEquals("LY0001", service.requisition(req(1L, 3)));

        ArgumentCaptor<MatRequisition> captured = ArgumentCaptor.forClass(MatRequisition.class);
        verify(requisitionMapper).insert(captured.capture());
        var breakdown = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(captured.getValue().getBreakdown());
        assertEquals(1, breakdown.size());
        assertEquals(batchNo, breakdown.get(0).get("batchNo").asText());
        assertEquals(3, breakdown.get(0).get("quantity").asInt());
    }

    @Test
    void approve_rejectsNonPending() {
        MatPurchase po = new MatPurchase(); po.setId(1L); po.setStatus(30);
        when(purchaseMapper.selectById(1L)).thenReturn(po);
        BizException e = assertThrows(BizException.class, () -> service.approve(1L));
        assertTrue(e.getMessage().contains("待审批"));
    }

    @Test
    void receive_rejectsExpiredBatch() {
        MatPurchase po = new MatPurchase(); po.setId(1L); po.setStatus(20);
        when(purchaseMapper.selectById(1L)).thenReturn(po);
        BizException e = assertThrows(BizException.class,
                () -> service.receive(1L, "B1", java.time.LocalDate.now().minusDays(1)));
        assertTrue(e.getMessage().contains("过期"));
    }
}
