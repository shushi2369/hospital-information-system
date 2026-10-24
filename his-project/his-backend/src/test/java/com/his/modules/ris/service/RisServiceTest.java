package com.his.modules.ris.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.alert.service.AlertService;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.clinic.mapper.CliExamApplicationMapper;
import com.his.modules.plt.service.PltService;
import com.his.modules.ris.dto.ReportReviewRequest;
import com.his.modules.ris.entity.RisReport;
import com.his.modules.ris.entity.RisRequest;
import com.his.modules.ris.gateway.ImagingGateway;
import com.his.modules.ris.mapper.RisAppointmentMapper;
import com.his.modules.ris.mapper.RisDeviceMapper;
import com.his.modules.ris.mapper.RisImageMapper;
import com.his.modules.ris.mapper.RisReportMapper;
import com.his.modules.ris.mapper.RisRequestMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** RisService 单测：报告审核双签（自审拦截）+ 驳回理由必填（112 轮 B09）+ 驳回行数断言。 */
class RisServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, RisReport.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, RisRequest.class);
    }

    private final RisRequestMapper requestMapper = mock(RisRequestMapper.class);
    private final com.his.modules.patient.app.PatientAppService patientAppService =
            mock(com.his.modules.patient.app.PatientAppService.class);
    private final RisDeviceMapper deviceMapper = mock(RisDeviceMapper.class);
    private final RisAppointmentMapper appointmentMapper = mock(RisAppointmentMapper.class);
    private final RisImageMapper imageMapper = mock(RisImageMapper.class);
    private final RisReportMapper reportMapper = mock(RisReportMapper.class);
    private final ImagingGateway imagingGateway = mock(ImagingGateway.class);
    private final AlertService alertService = mock(AlertService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGen = mock(IdGenerator.class);
    private final CliExamApplicationMapper clinicExamMapper = mock(CliExamApplicationMapper.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);

    private RisService svc() {
        return new RisService(requestMapper, patientAppService, deviceMapper, appointmentMapper,
                imageMapper, reportMapper, imagingGateway, alertService, pltService, idGen,
                clinicExamMapper, basedataAppService);
    }

    private RisReport report(Long id, int status, Long reporterId) {
        RisReport r = new RisReport();
        r.setId(id);
        r.setReportNo("XD-T-" + id);
        r.setRequestId(500L);
        r.setStatus(status);
        r.setReporterId(reporterId);
        return r;
    }

    private ReportReviewRequest review(Boolean approved, String reason) {
        ReportReviewRequest req = new ReportReviewRequest();
        req.setApproved(approved);
        req.setReason(reason);
        return req;
    }

    // ---------------- 驳回（112 轮 B09） ----------------

    @Test
    void reject_blankReasonRejected() {
        when(reportMapper.selectById(1L)).thenReturn(report(1L, 10, 2L));
        BizException e = assertThrows(BizException.class,
                () -> svc().reviewReport(1L, review(false, "  ")));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void reject_nullReasonRejected() {
        when(reportMapper.selectById(1L)).thenReturn(report(1L, 10, 2L));
        BizException e = assertThrows(BizException.class,
                () -> svc().reviewReport(1L, review(false, null)));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void reject_updatesRowAsserted() {
        when(reportMapper.selectById(1L)).thenReturn(report(1L, 10, 2L));
        when(reportMapper.updateById(any())).thenReturn(0); // 并发失败
        BizException e = assertThrows(BizException.class,
                () -> svc().reviewReport(1L, review(false, "质量不合格")));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void reject_successRecordsEvent() {
        when(reportMapper.selectById(1L)).thenReturn(report(1L, 10, 2L));
        when(reportMapper.updateById(any())).thenReturn(1);
        svc().reviewReport(1L, review(false, "质量不合格"));
        verify(pltService).recordEvent(eq("ris.report.rejected"), eq("XD-T-1"), any());
    }

    // ---------------- 通过（双签） ----------------

    @Test
    void approve_selfReviewRejected() {
        // 基类默认 userId=1；reporter 也是 1 → 自审拦截
        when(reportMapper.selectById(1L)).thenReturn(report(1L, 10, 1L));
        BizException e = assertThrows(BizException.class,
                () -> svc().reviewReport(1L, review(true, null)));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void approve_wrongStatusRejected() {
        when(reportMapper.selectById(1L)).thenReturn(report(1L, 20, 2L)); // 已发布
        BizException e = assertThrows(BizException.class,
                () -> svc().reviewReport(1L, review(true, null)));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void approve_secondSignerSuccess() {
        // userId=1（ADMIN），reporter=2 → 第二签名人通过
        RisReport r = report(1L, 10, 2L);
        when(reportMapper.selectById(1L)).thenReturn(r);
        when(reportMapper.updateById(any())).thenReturn(1);
        RisRequest req = new RisRequest();
        req.setId(500L);
        req.setStatus(30);
        req.setPatientId(9L);
        when(requestMapper.selectById(500L)).thenReturn(req);
        when(requestMapper.updateById(any())).thenReturn(1);
        // 门诊回写链 stub（无门诊申请 → null）
        when(clinicExamMapper.selectOne(any())).thenReturn(null);
        when(patientAppService.getById(any())).thenReturn(null);
        svc().reviewReport(1L, review(true, null));
        assertEquals(20, r.getStatus());
        assertEquals(40, req.getStatus(), "报告发布应推进申请 30→40");
    }
}
