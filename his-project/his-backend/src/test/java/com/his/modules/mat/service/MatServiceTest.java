package com.his.modules.mat.service;

import com.his.common.BizException;
import com.his.modules.mat.dto.MatRequisitionRequest;
import com.his.modules.mat.entity.MatMaterial;
import com.his.modules.mat.entity.MatPurchase;
import com.his.modules.mat.mapper.MatBatchMapper;
import com.his.modules.mat.mapper.MatMaterialMapper;
import com.his.modules.mat.mapper.MatPurchaseMapper;
import com.his.modules.mat.mapper.MatRequisitionMapper;
import com.his.modules.mat.mapper.MatStockMapper;
import com.his.modules.plt.service.PltService;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** MatService 核心逻辑单测：状态机 + 校验 + 原子扣减。 */
class MatServiceTest {

    private final MatMaterialMapper materialMapper = mock(MatMaterialMapper.class);
    private final MatStockMapper stockMapper = mock(MatStockMapper.class);
    private final MatPurchaseMapper purchaseMapper = mock(MatPurchaseMapper.class);
    private final MatRequisitionMapper requisitionMapper = mock(MatRequisitionMapper.class);
    private final MatBatchMapper batchMapper = mock(MatBatchMapper.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private final MatService service = new MatService(
            materialMapper, stockMapper, purchaseMapper, requisitionMapper,
            batchMapper, basedataAppService, pltService, idGenerator);

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
        when(stockMapper.update(any(), any())).thenReturn(0);
        BizException e = assertThrows(BizException.class, () -> service.requisition(req(1L, 1)));
        assertTrue(e.getMessage().contains("库存不足"));
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
