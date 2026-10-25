package com.his.modules.pe.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.pe.dto.PeRegisterRequest;
import com.his.modules.pe.dto.PeReportRequest;
import com.his.modules.pe.dto.PeResultRequest;
import com.his.modules.pe.entity.PePackage;
import com.his.modules.pe.entity.PeRecord;
import com.his.modules.pe.mapper.PePackageMapper;
import com.his.modules.pe.mapper.PeRecordMapper;
import com.his.modules.pe.mapper.PeReportMapper;
import com.his.modules.pe.mapper.PeResultMapper;
import com.his.modules.plt.service.PltService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PeService 单测：体检登记状态机 + 套餐在途保护（112 轮 B10）+ 分项齐全判定 + 总检发布。 */
class PeServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PePackage.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PeRecord.class);
    }

    private static final String ITEMS_JSON =
            "[{\"chargeItemId\":1,\"itemName\":\"血常规\"},{\"chargeItemId\":2,\"itemName\":\"尿常规\"}]";

    private final PePackageMapper packageMapper = mock(PePackageMapper.class);
    private final PeRecordMapper recordMapper = mock(PeRecordMapper.class);
    private final PeResultMapper resultMapper = mock(PeResultMapper.class);
    private final PeReportMapper reportMapper = mock(PeReportMapper.class);
    private final PatientAppService patientAppService = mock(PatientAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PeService svc() {
        return new PeService(packageMapper, recordMapper, resultMapper, reportMapper,
                patientAppService, pltService, idGenerator, objectMapper);
    }

    private PePackage pkg(int status) {
        PePackage p = new PePackage();
        p.setId(5L);
        p.setPackageNo("TC001");
        p.setName("基础体检");
        p.setPrice(new BigDecimal("199.00"));
        p.setItems(ITEMS_JSON);
        p.setStatus(status);
        return p;
    }

    private PeRecord record(int status) {
        PeRecord r = new PeRecord();
        r.setId(9L);
        r.setRecordNo("TJ001");
        r.setPackageId(5L);
        r.setStatus(status);
        return r;
    }

    private PeResultRequest resultReq(long chargeItemId) {
        PeResultRequest req = new PeResultRequest();
        req.setChargeItemId(chargeItemId);
        req.setItemName("血常规");
        req.setResultValue("正常");
        return req;
    }

    @Test
    void createPackage_emptyItems_throws() {
        com.his.modules.pe.dto.PePackageRequest req =
                new com.his.modules.pe.dto.PePackageRequest();
        req.setItems(List.of());
        BizException e = assertThrows(BizException.class, () -> svc().createPackage(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void createPackage_happy_serializesItems() {
        com.his.modules.pe.dto.PePackageRequest req =
                new com.his.modules.pe.dto.PePackageRequest();
        req.setName("入职体检");
        req.setPrice(new BigDecimal("99"));
        com.his.modules.pe.dto.PePackageRequest.PackageItem item =
                new com.his.modules.pe.dto.PePackageRequest.PackageItem();
        item.setChargeItemId(1L);
        req.setItems(List.of(item));
        when(idGenerator.next("TC")).thenReturn("TC002");

        String no = svc().createPackage(req);

        assertEquals("TC002", no);
        verify(packageMapper).insert(any(PePackage.class));
    }

    @Test
    void updatePackage_inFlightRecords_throws() {
        // 112 轮 B10：登记只存 packageId 无项目快照，存在未完成登记时禁改套餐项目集
        when(packageMapper.selectById(5L)).thenReturn(pkg(1));
        when(recordMapper.selectCount(any())).thenReturn(2L);
        com.his.modules.pe.dto.PePackageRequest req =
                new com.his.modules.pe.dto.PePackageRequest();
        req.setItems(List.of(new com.his.modules.pe.dto.PePackageRequest.PackageItem()));

        BizException e = assertThrows(BizException.class, () -> svc().updatePackage(5L, req));
        assertTrue(e.getMessage().contains("未完成"));
    }

    @Test
    void updatePackage_happy_whenAllDone() {
        when(packageMapper.selectById(5L)).thenReturn(pkg(1));
        when(recordMapper.selectCount(any())).thenReturn(0L);
        when(packageMapper.updateById(any(PePackage.class))).thenReturn(1);
        com.his.modules.pe.dto.PePackageRequest req =
                new com.his.modules.pe.dto.PePackageRequest();
        req.setName("改版套餐");
        req.setItems(List.of(new com.his.modules.pe.dto.PePackageRequest.PackageItem()));

        assertDoesNotThrow(() -> svc().updatePackage(5L, req));
        // 改动必须真实落库（弱用例修复：stub 即忘 → 落库证据）
        ArgumentCaptor<PePackage> captor = ArgumentCaptor.forClass(PePackage.class);
        verify(packageMapper).updateById(captor.capture());
        assertEquals("改版套餐", captor.getValue().getName());
    }

    @Test
    void register_inactivePackage_throws() {
        when(patientAppService.requireActive(11L)).thenReturn(new com.his.modules.patient.app.PatientDTO());
        when(packageMapper.selectById(5L)).thenReturn(pkg(0));
        PeRegisterRequest req = new PeRegisterRequest();
        req.setPatientId(11L);
        req.setPackageId(5L);
        BizException e = assertThrows(BizException.class, () -> svc().register(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void register_happy_status10() {
        when(patientAppService.requireActive(11L)).thenReturn(new com.his.modules.patient.app.PatientDTO());
        when(packageMapper.selectById(5L)).thenReturn(pkg(1));
        when(idGenerator.next("TJ")).thenReturn("TJ001");
        PeRegisterRequest req = new PeRegisterRequest();
        req.setPatientId(11L);
        req.setPackageId(5L);

        String no = svc().register(req);

        assertEquals("TJ001", no);
        // 状态 10（待检查）必须真实落库（弱用例修复）
        ArgumentCaptor<PeRecord> captor = ArgumentCaptor.forClass(PeRecord.class);
        verify(recordMapper).insert(captor.capture());
        assertEquals(10, captor.getValue().getStatus());
        verify(pltService).recordEvent(eq("pe.record.registered"), eq("TJ001"), anyString());
    }

    @Test
    void start_notPending_throws() {
        when(recordMapper.selectById(9L)).thenReturn(record(20));
        BizException e = assertThrows(BizException.class, () -> svc().start(9L));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void saveResult_itemNotInPackage_throws() {
        when(recordMapper.selectById(9L)).thenReturn(record(20));
        when(packageMapper.selectById(5L)).thenReturn(pkg(1));
        BizException e = assertThrows(BizException.class,
                () -> svc().saveResult(9L, resultReq(99L)));
        assertTrue(e.getMessage().contains("不在套餐内"));
    }

    @Test
    void saveResult_newInsert_andUpsertUpdate() {
        when(recordMapper.selectById(9L)).thenReturn(record(20));
        when(packageMapper.selectById(5L)).thenReturn(pkg(1));
        when(resultMapper.selectOne(any())).thenReturn(null);
        svc().saveResult(9L, resultReq(1L));
        verify(resultMapper).insert(any());

        // 二次录入同一分项 = 覆盖式更新（112 轮 B08 乐观锁断言）
        com.his.modules.pe.entity.PeResult existing =
                new com.his.modules.pe.entity.PeResult();
        existing.setId(3L);
        existing.setChargeItemId(1L);
        when(resultMapper.selectOne(any())).thenReturn(existing);
        when(resultMapper.updateById(any())).thenReturn(1);
        svc().saveResult(9L, resultReq(1L));
        verify(resultMapper).updateById(any());
    }

    @Test
    void saveResult_concurrentOverwriteZeroRow_throws() {
        // 112 轮 B08：覆盖式更新遇乐观锁 0 行必须报错，静默丢更新与"覆盖式"语义相反
        when(recordMapper.selectById(9L)).thenReturn(record(20));
        when(packageMapper.selectById(5L)).thenReturn(pkg(1));
        com.his.modules.pe.entity.PeResult existing =
                new com.his.modules.pe.entity.PeResult();
        existing.setId(3L);
        existing.setChargeItemId(1L);
        when(resultMapper.selectOne(any())).thenReturn(existing);
        when(resultMapper.updateById(any())).thenReturn(0);
        BizException e = assertThrows(BizException.class,
                () -> svc().saveResult(9L, resultReq(1L)));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void finish_missingItems_throws() {
        when(recordMapper.selectById(9L)).thenReturn(record(20));
        when(packageMapper.selectById(5L)).thenReturn(pkg(1));
        when(resultMapper.selectList(any())).thenReturn(List.of());
        BizException e = assertThrows(BizException.class, () -> svc().finish(9L));
        assertTrue(e.getMessage().contains("分项未录齐"));
    }

    @Test
    void finish_happy_allItemsRecorded() {
        when(recordMapper.selectById(9L)).thenReturn(record(20));
        when(packageMapper.selectById(5L)).thenReturn(pkg(1));
        com.his.modules.pe.entity.PeResult r1 =
                new com.his.modules.pe.entity.PeResult();
        r1.setChargeItemId(1L);
        com.his.modules.pe.entity.PeResult r2 =
                new com.his.modules.pe.entity.PeResult();
        r2.setChargeItemId(2L);
        when(resultMapper.selectList(any())).thenReturn(List.of(r1, r2));
        when(recordMapper.updateById(any(PeRecord.class))).thenReturn(1);

        assertDoesNotThrow(() -> svc().finish(9L));
        // 20→30 状态翻转必须有落库证据（弱用例修复：分项齐全链路唯一无证据一跳）
        ArgumentCaptor<PeRecord> captor = ArgumentCaptor.forClass(PeRecord.class);
        verify(recordMapper).updateById(captor.capture());
        assertEquals(30, captor.getValue().getStatus());
    }

    @Test
    void saveResult_raceDuplicateKey_throws() {
        // 并发双录撞 uk_peresult：预检窗口兜底 → A0008
        when(recordMapper.selectById(9L)).thenReturn(record(20));
        when(packageMapper.selectById(5L)).thenReturn(pkg(1));
        when(resultMapper.selectOne(any())).thenReturn(null);
        when(resultMapper.insert(any()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk"));
        BizException e = assertThrows(BizException.class,
                () -> svc().saveResult(9L, resultReq(1L)));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void publishReport_notFinished_throws() {
        when(recordMapper.selectById(9L)).thenReturn(record(20));
        BizException e = assertThrows(BizException.class,
                () -> svc().publishReport(9L, new PeReportRequest()));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void publishReport_duplicate_throws() {
        when(recordMapper.selectById(9L)).thenReturn(record(30));
        when(reportMapper.selectCount(any())).thenReturn(1L);
        BizException e = assertThrows(BizException.class,
                () -> svc().publishReport(9L, new PeReportRequest()));
        assertTrue(e.getMessage().contains("已发布"));
    }

    @Test
    void publishReport_happy() {
        when(recordMapper.selectById(9L)).thenReturn(record(30));
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(idGenerator.next("TJB")).thenReturn("TJB001");
        when(recordMapper.update(isNull(), any())).thenReturn(1);
        PeReportRequest req = new PeReportRequest();
        req.setSummary("总体未见异常");

        String no = svc().publishReport(9L, req);

        assertEquals("TJB001", no);
        verify(reportMapper).insert(any());
    }

    @Test
    void detail_aggregatesPendingItems() {
        when(recordMapper.selectById(9L)).thenReturn(record(20));
        when(packageMapper.selectById(5L)).thenReturn(pkg(1));
        com.his.modules.pe.entity.PeResult r1 =
                new com.his.modules.pe.entity.PeResult();
        r1.setChargeItemId(1L);
        when(resultMapper.selectList(any())).thenReturn(List.of(r1));
        when(reportMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> detail = svc().detail(9L);

        assertEquals(1, ((List<?>) detail.get("pendingItems")).size());
        assertEquals(2, ((List<?>) detail.get("allItems")).size());
    }
}
