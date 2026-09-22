package com.his.modules.doc.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.ChargeItemDTO;
import com.his.modules.doc.dto.StopOrderRequest;
import com.his.modules.doc.entity.DocOrder;
import com.his.modules.doc.entity.DocOrderExec;
import com.his.modules.doc.entity.DocOrderItem;
import com.his.modules.doc.mapper.DocOrderExecMapper;
import com.his.modules.doc.mapper.DocOrderItemMapper;
import com.his.modules.doc.mapper.DocOrderMapper;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.plt.service.PltService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** DocOrderService 核心逻辑单测：执行单并发占位（三十一轮修复）+ 门禁 + 状态机。 */
class DocOrderServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        // 纯 Mockito 环境无 MP 容器：LambdaUpdateWrapper.set(实体::getter) 需要实体 lambda cache
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        for (Class<?> entity : new Class<?>[]{DocOrder.class, DocOrderItem.class, DocOrderExec.class}) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    private final DocOrderMapper orderMapper = mock(DocOrderMapper.class);
    private final DocOrderItemMapper itemMapper = mock(DocOrderItemMapper.class);
    private final DocOrderExecMapper execMapper = mock(DocOrderExecMapper.class);
    private final InpAppService inpAppService = mock(InpAppService.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final com.his.modules.pharmacy.service.InventoryService inventoryService =
            mock(com.his.modules.pharmacy.service.InventoryService.class);
    private final com.his.modules.lis.service.LisService lisAppService =
            mock(com.his.modules.lis.service.LisService.class);
    private final com.his.modules.ris.service.RisService risAppService =
            mock(com.his.modules.ris.service.RisService.class);
    private final com.his.modules.cdss.service.CdssService cdssAppService =
            mock(com.his.modules.cdss.service.CdssService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private final DocOrderService service = new DocOrderService(
            orderMapper, itemMapper, execMapper, inpAppService, basedataAppService,
            inventoryService, lisAppService, risAppService, cdssAppService, pltService, idGenerator);

    private DocOrder order(int orderClass, int category, int status) {
        DocOrder o = new DocOrder();
        o.setId(100L);
        o.setOrderNo("ORD100");
        o.setAdmissionId(900L);
        o.setOrderClass(orderClass);
        o.setCategory(category);
        o.setStatus(status);
        return o;
    }

    private DocOrderExec exec() {
        DocOrderExec e = new DocOrderExec();
        e.setId(5L);
        e.setOrderId(100L);
        e.setItemId(11L);
        e.setExecType(2);
        e.setStatus(1);
        return e;
    }

    @Test
    void execute_winnerProceedsAndChargesOnce() {
        DocOrderExec e = exec();
        when(execMapper.selectById(5L)).thenReturn(e);
        when(orderMapper.selectById(100L)).thenReturn(order(2, 4, 30));
        when(execMapper.update(any(), any())).thenReturn(1);
        DocOrderItem item = new DocOrderItem();
        item.setId(11L);
        item.setChargeItemId(77L);
        item.setItemName("换药");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("12.50"));
        when(itemMapper.selectById(11L)).thenReturn(item);
        ChargeItemDTO c = new ChargeItemDTO();
        c.setCategory(4);
        when(basedataAppService.getChargeItem(77L)).thenReturn(c);
        when(inpAppService.addExecFee(any(), any(), any(), any(), any(), any())).thenReturn(555L);

        service.execute(5L);

        verify(execMapper, times(1)).update(any(), any());
        verify(inpAppService, times(1)).addExecFee(any(), any(), any(), any(), any(), any());
        verify(execMapper).updateById(e);
        assertEquals(555L, e.getChargeDetailId());
    }

    @Test
    void execute_raceLoserGetsB6103AndNeverDoubleCharges() {
        when(execMapper.selectById(5L)).thenReturn(exec());
        when(orderMapper.selectById(100L)).thenReturn(order(2, 4, 30));
        when(execMapper.update(any(), any())).thenReturn(0); // 条件更新 0 行 = 已被并发方执行

        BizException e = assertThrows(BizException.class, () -> service.execute(5L));
        assertEquals(ErrorCode.B6103, e.getErrorCode());
        verify(inpAppService, never()).addExecFee(any(), any(), any(), any(), any(), any());
        verify(lisAppService, never()).createRequestFromOrder(any(), any(), any());
        verify(risAppService, never()).createRequestFromOrder(any(), any(), any());
    }

    @Test
    void execute_stoppedOrderRejected() {
        when(execMapper.selectById(5L)).thenReturn(exec());
        when(orderMapper.selectById(100L)).thenReturn(order(1, 4, 50));

        BizException e = assertThrows(BizException.class, () -> service.execute(5L));
        assertEquals(ErrorCode.B6105, e.getErrorCode());
        verify(execMapper, never()).update(any(), any());
    }

    @Test
    void execute_drugOrderNotDispensedRejected() {
        when(execMapper.selectById(5L)).thenReturn(exec());
        when(orderMapper.selectById(100L)).thenReturn(order(1, 1, 20));

        BizException e = assertThrows(BizException.class, () -> service.execute(5L));
        assertTrue(e.getMessage().contains("未摆药"));
        verify(execMapper, never()).update(any(), any());
    }

    @Test
    void stop_setsStatus50AndSkipsFutureExecs() {
        DocOrder o = order(1, 4, 30);
        when(orderMapper.selectById(100L)).thenReturn(o);
        when(orderMapper.updateById(o)).thenReturn(1);
        StopOrderRequest req = new StopOrderRequest();
        req.setReason("病情好转");

        service.stop(100L, req);

        assertEquals(50, o.getStatus());
        verify(execMapper).update(any(), any()); // 未来执行计划置跳过
    }

    @Test
    void stop_statusChangedByOthersRejected() {
        DocOrder o = order(1, 4, 30);
        when(orderMapper.selectById(100L)).thenReturn(o);
        when(orderMapper.updateById(o)).thenReturn(0); // 乐观锁 0 行

        assertThrows(BizException.class, () -> service.stop(100L, new StopOrderRequest()));
    }

    @Test
    void voidOrder_executedOrderRejected() {
        when(orderMapper.selectById(100L)).thenReturn(order(2, 4, 30));

        BizException e = assertThrows(BizException.class,
                () -> service.voidOrder(100L, new StopOrderRequest()));
        assertEquals(ErrorCode.B6106, e.getErrorCode());
    }
}
