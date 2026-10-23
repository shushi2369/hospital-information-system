package com.his.modules.rpt.service;

import com.his.common.BizException;
import com.his.modules.plt.service.PltService;
import com.his.modules.rpt.entity.RptUpload;
import com.his.modules.rpt.mapper.RptUploadMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上报失败退避序列（V50，八十八轮实测教训：退避滞留会放大队头阻塞，时序必须固化）。
 * 60s 起指数翻倍、封顶 600s；第 5 次失败转 FAILED（MAX_RETRY），退避只约束 1~4 次。
 */
class RptServiceTest {

    @BeforeAll
    static void initMpLambdaCache() {
        // 纯 Mockito 环境无 MP 容器：LambdaUpdateWrapper.set(实体::getter) 需要实体 lambda cache
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, RptUpload.class);
    }

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

    private RptService svc(RptUploadMapper mapper) {
        return new RptService(mapper, Mockito.mock(com.his.infrastructure.util.IdGenerator.class),
                Mockito.mock(com.fasterxml.jackson.databind.ObjectMapper.class), Mockito.mock(PltService.class));
    }

    @Test
    void retryNow_rejectsPendingToAvoidSecondExternalSend() {
        RptUploadMapper mapper = Mockito.mock(RptUploadMapper.class);
        RptUpload up = new RptUpload();
        up.setId(1L);
        up.setUploadNo("RS1");
        up.setStatus(RptUpload.STATUS_PENDING);
        when(mapper.selectById(1L)).thenReturn(up);
        BizException e = assertThrows(BizException.class, () -> svc(mapper).retryNow(1L));
        assertTrue(e.getMessage().contains("队列"));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void retryNow_failsFastWhenConcurrentTransitionWins() {
        RptUploadMapper mapper = Mockito.mock(RptUploadMapper.class);
        RptUpload up = new RptUpload();
        up.setId(1L);
        up.setUploadNo("RS1");
        up.setStatus(RptUpload.STATUS_FAILED);
        when(mapper.selectById(1L)).thenReturn(up);
        // 条件更新影响 0 行：另一请求已重报/状态已变化
        when(mapper.update(any(), any())).thenReturn(0);
        BizException e = assertThrows(BizException.class, () -> svc(mapper).retryNow(1L));
        assertTrue(e.getMessage().contains("已变化"));
        // 条件更新只尝试一次；失败即抛错，不得越过它继续投递（否则同一单可能双份外发）
        verify(mapper, Mockito.times(1)).update(any(), any());
        verify(mapper, Mockito.times(1)).selectById(1L);
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
