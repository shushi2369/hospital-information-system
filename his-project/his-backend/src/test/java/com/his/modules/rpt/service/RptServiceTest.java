package com.his.modules.rpt.service;

import com.his.modules.plt.service.PltService;
import com.his.modules.rpt.mapper.RptUploadMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上报失败退避序列（V50，八十八轮实测教训：退避滞留会放大队头阻塞，时序必须固化）。
 * 60s 起指数翻倍、封顶 600s；第 5 次失败转 FAILED（MAX_RETRY），退避只约束 1~4 次。
 */
class RptServiceTest {

    @Test
    void backoffSequence_doublesAndCaps() {
        assertEquals(60, RptService.backoffSeconds(1));
        assertEquals(120, RptService.backoffSeconds(2));
        assertEquals(240, RptService.backoffSeconds(3));
        assertEquals(480, RptService.backoffSeconds(4));
        // 封顶：即使次数继续增长也不超过 10 分钟
        assertEquals(600, RptService.backoffSeconds(5));
        assertEquals(600, RptService.backoffSeconds(50));
    }

    @Test
    void backoffStaysWithinScheduledHorizon() {
        // 任何单次退避都不得超过 10 分钟——超过会让演示/排障时"看起来卡死"
        for (int i = 1; i <= 20; i++) {
            assertTrue(RptService.backoffSeconds(i) <= 600);
        }
    }

    @Test
    void constructsWithDeps_onlyForCoverageOfDIContract() {
        // @RequiredArgsConstructor 注入面回归：新增依赖时此处编译失败即提醒同步测试构造
        new RptService(
                org.mockito.Mockito.mock(RptUploadMapper.class),
                org.mockito.Mockito.mock(com.his.infrastructure.util.IdGenerator.class),
                org.mockito.Mockito.mock(com.fasterxml.jackson.databind.ObjectMapper.class),
                org.mockito.Mockito.mock(PltService.class));
    }
}
