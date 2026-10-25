package com.his.modules.basedata.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.basedata.dto.DrugCreateRequest;
import com.his.modules.basedata.dto.DrugUpdateRequest;
import com.his.modules.basedata.entity.Drug;
import com.his.modules.basedata.mapper.DrugMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** DrugService 单测：药品编码查重 + 状态取值门禁 + 缺省值兜底（库存预警 0/抗生素 0）。 */
class DrugServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Drug.class);
    }

    private final DrugMapper drugMapper = mock(DrugMapper.class);

    private DrugService svc() {
        return new DrugService(drugMapper);
    }

    private DrugCreateRequest createReq() {
        DrugCreateRequest r = new DrugCreateRequest();
        r.setDrugCode("YP001");
        r.setDrugName("阿莫西林胶囊");
        r.setSpec("0.25g*24");
        r.setUnit("盒");
        r.setRetailPrice(new BigDecimal("12.80"));
        return r;
    }

    @Test
    void create_codeDuplicate_throws() {
        when(drugMapper.selectCount(any())).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> svc().create(createReq()));
        assertEquals(ErrorCode.B5001, e.getErrorCode());
    }

    @Test
    void create_invalidStatus_throws() {
        DrugCreateRequest req = createReq();
        req.setStatus(2);
        BizException e = assertThrows(BizException.class, () -> svc().create(req));
        assertTrue(e.getMessage().contains("状态取值"));
    }

    @Test
    void create_happy_defaultsWarningZeroAntibiotic0() {
        when(drugMapper.selectCount(any())).thenReturn(0L);

        Drug created = svc().create(createReq());

        assertEquals(0, BigDecimal.ZERO.compareTo(created.getStockWarningQty()));
        assertEquals(0, created.getIsAntibiotic());
        assertEquals(1, created.getStatus());
    }

    @Test
    void create_raceDuplicateKey_throws() {
        when(drugMapper.selectCount(any())).thenReturn(0L);
        when(drugMapper.insert(any(Drug.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk"));
        BizException e = assertThrows(BizException.class, () -> svc().create(createReq()));
        assertEquals(ErrorCode.B5001, e.getErrorCode());
    }

    @Test
    void update_notFound_throws() {
        when(drugMapper.selectById(1L)).thenReturn(null);
        BizException e = assertThrows(BizException.class,
                () -> svc().update(1L, new DrugUpdateRequest()));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void update_invalidStatus_throws() {
        when(drugMapper.selectById(1L)).thenReturn(new Drug());
        DrugUpdateRequest req = new DrugUpdateRequest();
        req.setStatus(9);
        BizException e = assertThrows(BizException.class, () -> svc().update(1L, req));
        assertTrue(e.getMessage().contains("状态取值"));
    }

    @Test
    void update_happy_appliesOnlyProvidedFields() {
        Drug existing = new Drug();
        existing.setId(1L);
        existing.setDrugCode("YP001");
        existing.setDrugName("旧名");
        existing.setIsAntibiotic(0);
        when(drugMapper.selectById(1L)).thenReturn(existing);
        DrugUpdateRequest req = new DrugUpdateRequest();
        req.setDrugName("阿莫西林胶囊");
        req.setRetailPrice(new BigDecimal("15.00"));

        Drug updated = svc().update(1L, req);

        assertEquals("阿莫西林胶囊", updated.getDrugName());
        assertEquals(0, new BigDecimal("15.00").compareTo(updated.getRetailPrice()));
        assertEquals(0, updated.getIsAntibiotic());
        verify(drugMapper).updateById(existing);
    }
}
