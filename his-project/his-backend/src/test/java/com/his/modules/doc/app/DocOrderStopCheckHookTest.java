package com.his.modules.doc.app;

import com.his.UnitTestBase;
import com.his.modules.doc.entity.DocOrder;
import com.his.modules.doc.mapper.DocOrderMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 出院医嘱门禁：临时药已摆药未执行必须阻断（出院后 execute/void 均不可用）。 */
class DocOrderStopCheckHookTest extends UnitTestBase {

    private final DocOrderMapper orderMapper = mock(DocOrderMapper.class);
    private final DocOrderStopCheckHook hook = new DocOrderStopCheckHook(orderMapper);

    @Test
    void blocksDispensedTempDrugWithoutExecution() {
        DocOrder dispensed = new DocOrder();
        dispensed.setId(1L);
        when(orderMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(dispensed));
        String blocker = hook.checkBlocker(9L);
        assertNotNull(blocker);
        assertTrue(blocker.contains("已摆药未执行"), blocker);
    }

    @Test
    void passesWhenNoActiveOrder() {
        when(orderMapper.selectList(any())).thenReturn(List.of());
        assertNull(hook.checkBlocker(9L));
    }
}
