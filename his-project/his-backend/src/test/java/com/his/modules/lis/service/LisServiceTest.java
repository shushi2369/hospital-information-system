package com.his.modules.lis.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.alert.mapper.AlertCriticalMapper;
import com.his.modules.alert.service.AlertService;
import com.his.modules.lis.dto.ResultEntryRequest;
import com.his.modules.lis.entity.LisCriticalThreshold;
import com.his.modules.lis.entity.LisReport;
import com.his.modules.lis.entity.LisRequest;
import com.his.modules.lis.entity.LisResult;
import com.his.modules.lis.entity.LisSpecimen;
import com.his.modules.lis.gateway.LabInstrumentGateway;
import com.his.modules.lis.mapper.LisCriticalThresholdMapper;
import com.his.modules.lis.mapper.LisReportMapper;
import com.his.modules.lis.mapper.LisRequestMapper;
import com.his.modules.lis.mapper.LisResultMapper;
import com.his.modules.lis.mapper.LisSpecimenMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** LisService 单测：标本采集状态机 + 危急判定阈值边界 + 重录关断旧危急值（114 轮 #4/#5 固化）。 */
class LisServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, LisRequest.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, LisSpecimen.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, LisResult.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, LisReport.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, LisCriticalThreshold.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, com.his.modules.alert.entity.AlertCritical.class);
    }

    private final LisRequestMapper requestMapper = mock(LisRequestMapper.class);
    private final AlertCriticalMapper alertMapper = mock(AlertCriticalMapper.class);
    private final LisSpecimenMapper specimenMapper = mock(LisSpecimenMapper.class);
    private final LisResultMapper resultMapper = mock(LisResultMapper.class);
    private final LisReportMapper reportMapper = mock(LisReportMapper.class);
    private final LisCriticalThresholdMapper thresholdMapper = mock(LisCriticalThresholdMapper.class);
    private final LabInstrumentGateway instrumentGateway = mock(LabInstrumentGateway.class);
    private final AlertService alertService = mock(AlertService.class);
    private final com.his.modules.plt.service.PltService pltService =
            mock(com.his.modules.plt.service.PltService.class);
    private final com.his.modules.basedata.app.BasedataAppService basedataAppService =
            mock(com.his.modules.basedata.app.BasedataAppService.class);
    private final com.his.modules.patient.app.PatientAppService patientAppService =
            mock(com.his.modules.patient.app.PatientAppService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private LisService svc() {
        return new LisService(requestMapper, alertMapper, specimenMapper, resultMapper,
                reportMapper, thresholdMapper, instrumentGateway, alertService, pltService,
                basedataAppService, patientAppService, idGenerator);
    }

    private LisRequest request(int status) {
        LisRequest r = new LisRequest();
        r.setId(70L);
        r.setRequestNo("JY001");
        r.setStatus(status);
        return r;
    }

    private LisCriticalThreshold threshold(String high, String low) {
        LisCriticalThreshold t = new LisCriticalThreshold();
        t.setItemName("血钾");
        t.setHighValue(high == null ? null : new BigDecimal(high));
        t.setLowValue(low == null ? null : new BigDecimal(low));
        t.setStatus(1);
        return t;
    }

    private ResultEntryRequest.ResultRow row(String name, String value, Integer abnormal) {
        ResultEntryRequest.ResultRow row = new ResultEntryRequest.ResultRow();
        row.setItemName(name);
        row.setResultValue(value);
        row.setAbnormalFlag(abnormal);
        return row;
    }

    @Test
    void collect_notPending_throws() {
        when(requestMapper.selectById(70L)).thenReturn(request(20));
        BizException e = assertThrows(BizException.class, () -> svc().collect(70L));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void collect_happy_createsSpecimenAndFlipsStatus() {
        when(requestMapper.selectById(70L)).thenReturn(request(10));
        when(specimenMapper.selectOne(any())).thenReturn(null);
        when(idGenerator.next("BB")).thenReturn("BB001");
        when(requestMapper.update(isNull(), any())).thenReturn(1);

        var out = svc().collect(70L);

        assertEquals("JY001", out.get("requestNo"));
        assertEquals("BB001", out.get("specimenNo"));
        verify(specimenMapper).insert(any(LisSpecimen.class));
        verify(requestMapper).update(isNull(), any());
    }

    @Test
    void collect_concurrentZeroRow_throws() {
        when(requestMapper.selectById(70L)).thenReturn(request(10));
        when(specimenMapper.selectOne(any())).thenReturn(null);
        when(requestMapper.update(isNull(), any())).thenReturn(0);
        BizException e = assertThrows(BizException.class, () -> svc().collect(70L));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void receive_notCollected_throws() {
        when(requestMapper.selectById(70L)).thenReturn(request(10));
        BizException e = assertThrows(BizException.class, () -> svc().receive(70L));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void receive_happy_toTesting() {
        when(requestMapper.selectById(70L)).thenReturn(request(20));
        when(specimenMapper.selectOne(any())).thenReturn(new LisSpecimen());
        when(requestMapper.update(isNull(), any())).thenReturn(1);

        assertDoesNotThrow(() -> svc().receive(70L));
        verify(specimenMapper).updateById(any(LisSpecimen.class));
    }

    @Test
    void entry_notInTesting_throws() {
        when(requestMapper.selectOne(any())).thenReturn(request(20));
        ResultEntryRequest req = new ResultEntryRequest();
        req.setRequestId(70L);
        req.setFetch(false);
        BizException e = assertThrows(BizException.class, () -> svc().entry(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void entry_manualEmptyRows_throws() {
        when(requestMapper.selectOne(any())).thenReturn(request(30));
        ResultEntryRequest req = new ResultEntryRequest();
        req.setRequestId(70L);
        req.setFetch(false);
        BizException e = assertThrows(BizException.class, () -> svc().entry(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void entry_highValue_createsCriticalAlert() {
        when(requestMapper.selectOne(any())).thenReturn(request(30));
        ResultEntryRequest req = new ResultEntryRequest();
        req.setRequestId(70L);
        req.setFetch(false);
        req.setRows(List.of(row("血钾", "7.0", 1)));
        when(thresholdMapper.selectOne(any())).thenReturn(threshold("5.5", null));

        int criticalCount = svc().entry(req);

        assertEquals(1, criticalCount);
        // resultId 由 MyBatis 回填，单测 mock 不回填故为 null
        verify(alertService).create(any(LisRequest.class), isNull(), eq("血钾"), eq("7.0"));
        verify(resultMapper).insert(any(LisResult.class));
        // 重录前关断旧危急值（114 轮 #4）
        verify(alertMapper).update(isNull(), any());
    }

    @Test
    void entry_blankValue_noCriticalNoNpe() {
        // 114 轮修复固化：空结果不再 NPE 回滚整次录入，仅不判危急
        when(requestMapper.selectOne(any())).thenReturn(request(30));
        ResultEntryRequest req = new ResultEntryRequest();
        req.setRequestId(70L);
        req.setFetch(false);
        req.setRows(List.of(row("血钾", "  ", 0)));
        when(thresholdMapper.selectOne(any())).thenReturn(threshold("5.5", null));

        assertEquals(0, svc().entry(req));
        verify(alertService, never()).create(any(), anyLong(), any(), any());
    }

    @Test
    void entry_nonNumericValue_noCritical() {
        when(requestMapper.selectOne(any())).thenReturn(request(30));
        ResultEntryRequest req = new ResultEntryRequest();
        req.setRequestId(70L);
        req.setFetch(false);
        req.setRows(List.of(row("白细胞分类", "未见异常", 0)));
        when(thresholdMapper.selectOne(any())).thenReturn(threshold("5.5", "3.5"));

        assertEquals(0, svc().entry(req));
        verify(alertService, never()).create(any(), anyLong(), any(), any());
    }

    @Test
    void entry_publishedMidway_throws() {
        // 114 轮 #5：录入全程申请须停留在检验中——锁读发现已发布则整体回滚
        when(requestMapper.selectOne(any()))
                .thenReturn(request(30))
                .thenReturn(request(40));
        ResultEntryRequest req = new ResultEntryRequest();
        req.setRequestId(70L);
        req.setFetch(false);
        req.setRows(List.of(row("血钾", "7.0", 1)));
        when(thresholdMapper.selectOne(any())).thenReturn(threshold("5.5", null));

        BizException e = assertThrows(BizException.class, () -> svc().entry(req));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void publish_noResults_throws() {
        when(requestMapper.selectOne(any())).thenReturn(request(30));
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(resultMapper.selectList(any())).thenReturn(List.of());
        when(resultMapper.selectCount(any())).thenReturn(0L);

        BizException e = assertThrows(BizException.class, () -> svc().publish(70L, 1, null));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void publish_duplicateReport_throws() {
        when(requestMapper.selectOne(any())).thenReturn(request(30));
        when(reportMapper.selectCount(any())).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> svc().publish(70L, 1, null));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void publish_happy_summaryListsCriticalItems() {
        when(requestMapper.selectOne(any())).thenReturn(request(30));
        when(reportMapper.selectCount(any())).thenReturn(0L);
        LisResult critical = new LisResult();
        critical.setItemName("血钾");
        critical.setCriticalFlag(1);
        when(resultMapper.selectList(any())).thenReturn(List.of(critical));
        when(resultMapper.selectCount(any())).thenReturn(1L);
        when(idGenerator.next("BG")).thenReturn("BG001");
        when(requestMapper.update(isNull(), any())).thenReturn(1);

        String reportNo = svc().publish(70L, 1, null);

        assertEquals("BG001", reportNo);
        verify(reportMapper).insert(any(LisReport.class));
        verify(pltService).recordEvent(eq("lis.report.published"), eq("BG001"), anyString());
    }
}
