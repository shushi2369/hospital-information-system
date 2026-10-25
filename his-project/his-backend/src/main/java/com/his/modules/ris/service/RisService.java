package com.his.modules.ris.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.modules.alert.service.AlertService;
import com.his.modules.doc.entity.DocOrder;
import com.his.modules.doc.entity.DocOrderItem;
import com.his.modules.plt.service.PltService;
import com.his.modules.ris.dto.*;
import com.his.modules.ris.entity.*;
import com.his.modules.ris.gateway.ImagingGateway;
import com.his.modules.ris.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RIS 影像检查闭环服务（《15》§2.2）：护士执行检查医嘱自动生成申请 → 预约 → 执行 → 影像(Mock) → 报告双签 → 发布。
 * 危急征象走危急值 source=2（复用第一批 alert 闭环）；检查费仍"执行即计费"不在此处。
 */
@Service
@lombok.extern.slf4j.Slf4j
@RequiredArgsConstructor
public class RisService {
    private final RisRequestMapper requestMapper;
    private final com.his.modules.patient.app.PatientAppService patientAppService;
    private final RisDeviceMapper deviceMapper;
    private final RisAppointmentMapper appointmentMapper;
    private final RisImageMapper imageMapper;
    private final RisReportMapper reportMapper;
    private final ImagingGateway imagingGateway;
    private final AlertService alertService;
    private final PltService pltService;
    private final IdGenerator idGenerator;
    private final com.his.modules.clinic.mapper.CliExamApplicationMapper clinicExamApplicationMapper;
    private final com.his.modules.basedata.app.BasedataAppService basedataAppService;

    /** 护士执行检查医嘱时自动生成申请单（doc → ris 单向调用，同 LIS 模式，《12》§3） */
    @Transactional
    public String createRequestFromOrder(DocOrder order, DocOrderItem item, Long executorId) {
        RisRequest request = new RisRequest();
        request.setRequestNo(idGenerator.next("JC"));
        request.setOrderId(order.getId());
        request.setAdmissionId(order.getAdmissionId());
        request.setPatientId(order.getPatientId());
        request.setDoctorId(order.getDoctorId());
        request.setModality(inferModality(item == null ? null : item.getItemName()));
        request.setBodyPart("通用");
        request.setUrgency(1);
        request.setStatus(10);
        requestMapper.insert(request);
        pltService.recordEvent("ris.request.created", request.getRequestNo(),
                "{\"orderId\":" + order.getId() + "}");
        return request.getRequestNo();
    }

    /** 医嘱作废联动（防御性，《16》§4.2）：待预约申请随医嘱作废 */
    @Transactional
    public void voidByOrder(Long orderId) {
        RisRequest request = requestMapper.selectOne(new LambdaQueryWrapper<RisRequest>()
                .eq(RisRequest::getOrderId, orderId)
                .eq(RisRequest::getStatus, 10)
                .last("LIMIT 1"));
        if (request != null) {
            // 条件更新：与技师预约并发时，已被预约(20)的申请不得回退作废
            int updated = requestMapper.update(null, new LambdaUpdateWrapper<RisRequest>()
                    .eq(RisRequest::getId, request.getId())
                    .eq(RisRequest::getStatus, 10)
                    .set(RisRequest::getStatus, 50)
                    .set(RisRequest::getUpdatedAt, LocalDateTime.now()));
            if (updated == 1) {
                pltService.recordEvent("ris.request.voided", request.getRequestNo(),
                        "{\"orderId\":" + orderId + "}");
            }
        }
    }

    /**
     * 四期二批：门诊检查联动（billing → ris 单向 hook，同事务）。
     * 支付完成后扫描该就诊的已收费检查申请，逐条生成检查申请并回填幂等标记；
     * 全部异常内部消化（log），不影响收费主事务。
     */
    @Transactional
    public void createOutpatientRequests(Long visitId) {
        List<com.his.modules.clinic.entity.CliExamApplication> pending;
        try {
            pending = clinicExamApplicationMapper.selectList(
                    new LambdaQueryWrapper<com.his.modules.clinic.entity.CliExamApplication>()
                            .eq(com.his.modules.clinic.entity.CliExamApplication::getVisitId, visitId)
                            .eq(com.his.modules.clinic.entity.CliExamApplication::getApplyType, 1)
                            .eq(com.his.modules.clinic.entity.CliExamApplication::getStatus, 20)
                            .isNull(com.his.modules.clinic.entity.CliExamApplication::getRisRequestId));
        } catch (Exception e) {
            log.warn("门诊检查联动查询失败 visit {}", visitId, e);
            return;
        }
        // 逐条隔离：原整段 try 遇到单条异常会中断后续申请，导致部分已收费检查无 RIS 单
        for (com.his.modules.clinic.entity.CliExamApplication exam : pending) {
            try {
                RisRequest request = new RisRequest();
                request.setRequestNo(idGenerator.next("JC"));
                request.setVisitId(visitId);
                request.setPatientId(exam.getPatientId());
                request.setDoctorId(exam.getDoctorId());
                var item = basedataAppService.getChargeItem(exam.getChargeItemId());
                request.setModality(inferModality(item == null ? "" : item.getItemName()));
                request.setBodyPart("通用");
                request.setUrgency(1);
                request.setStatus(10);
                requestMapper.insert(request);
                // 回填幂等标记：同一检查申请仅生成一条检查申请
                exam.setRisRequestId(request.getId());
                clinicExamApplicationMapper.updateById(exam);
                pltService.recordEvent("ris.request.created", request.getRequestNo(),
                        "{\"visitId\":" + visitId + ",\"outpatient\":true}");
            } catch (Exception e) {
                log.warn("门诊检查联动失败 visit {} exam {}", visitId, exam.getId(), e);
                pltService.recordEvent("ris.outpatient.item.failed", String.valueOf(exam.getId()),
                        "{\"visitId\":" + visitId + "}");
            }
        }
    }

    /** 门诊检查申请引用查询（报告发布回写用） */
    private com.his.modules.clinic.entity.CliExamApplication findOutpatientExam(Long risRequestId) {
        return clinicExamApplicationMapper.selectOne(
                new LambdaQueryWrapper<com.his.modules.clinic.entity.CliExamApplication>()
                        .eq(com.his.modules.clinic.entity.CliExamApplication::getRisRequestId, risRequestId)
                        .last("LIMIT 1"));
    }

    /** 申请分页（R-01，技师工作池） */
    public PageResult<RisRequest> page(RisRequestQuery query) {
        Page<RisRequest> page = requestMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<RisRequest>()
                        .eq(query.getAdmissionId() != null, RisRequest::getAdmissionId, query.getAdmissionId())
                        .eq(query.getPatientId() != null, RisRequest::getPatientId, query.getPatientId())
                        .eq(query.getStatus() != null, RisRequest::getStatus, query.getStatus())
                        .eq(query.getModality() != null, RisRequest::getModality, query.getModality())
                        .eq(query.getUrgency() != null, RisRequest::getUrgency, query.getUrgency())
                        .orderByAsc(RisRequest::getStatus).orderByDesc(RisRequest::getId));
        com.his.infrastructure.util.PatientNameBackfill.fill(page.getRecords(), patientAppService);
        return PageResult.of(page);
    }

    /** 申请详情聚合（R-02）：申请 + 预约 + 影像 + 报告 */
    public Map<String, Object> detail(Long id) {
        RisRequest request = requireRequest(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request", request);
        result.put("appointment", appointmentMapper.selectOne(new LambdaQueryWrapper<RisAppointment>()
                .eq(RisAppointment::getRequestId, id).last("LIMIT 1")));
        result.put("image", imageMapper.selectOne(new LambdaQueryWrapper<RisImage>()
                .eq(RisImage::getRequestId, id).last("LIMIT 1")));
        result.put("report", reportMapper.selectOne(new LambdaQueryWrapper<RisReport>()
                .eq(RisReport::getRequestId, id).last("LIMIT 1")));
        return result;
    }

    /** 预约（R-03）：10 → 20，设备检查类别须匹配 */
    @Transactional
    public Long appoint(Long id, AppointRequest req) {
        // 行锁：与退费联动作废互斥，避免“退费后仍可预约”
        RisRequest request = requireRequestForUpdate(id);
        if (request.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "申请不在待预约状态");
        }
        // 一百五十六轮 C-1：设备行锁（且为事务内首条普通读前的锁定读）——同设备同时段
        // 并发预约的 count-then-insert 竞态窗口（实测双双成功）。锁定读不建一致性快照，
        // 后续冲突 count 的快照在获锁后建立，能看到已提交的并发预约行
        RisDevice device = deviceMapper.selectByIdForUpdate(req.getDeviceId());
        if (device == null || device.getStatus() == null || device.getStatus() != 1) {
            throw new BizException(ErrorCode.A0001, "设备不可用");
        }
        if (device.getModality() == null || !device.getModality().equals(request.getModality())) {
            throw new BizException(ErrorCode.A0001, "设备检查类别与申请不匹配");
        }
        Long exists = appointmentMapper.selectCount(new LambdaQueryWrapper<RisAppointment>()
                .eq(RisAppointment::getRequestId, id)
                .ne(RisAppointment::getStatus, 3));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.A0001, "该申请已预约");
        }
        // 一百四十九轮 D-15：同设备同时段占用校验——原零冲突校验，三单同设备同时刻
        // 全部预约成功（检查当天才发现撞台）。status 1 待检查/2 检查中均算占用（3 已作废释放）
        Long occupied = appointmentMapper.selectCount(new LambdaQueryWrapper<RisAppointment>()
                .eq(RisAppointment::getDeviceId, req.getDeviceId())
                .eq(RisAppointment::getApptTime, req.getApptTime())
                .in(RisAppointment::getStatus, 1, 2));
        if (occupied != null && occupied > 0) {
            throw new BizException(ErrorCode.A0001, "该设备该时段已预约，请更换时段或设备");
        }
        RisAppointment appointment = new RisAppointment();
        appointment.setRequestId(id);
        appointment.setDeviceId(req.getDeviceId());
        appointment.setApptTime(req.getApptTime());
        appointment.setStatus(1);
        try {
            appointmentMapper.insert(appointment);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // uk_ris_appt_req：同一检查申请只能预约一次（并发双击兜底）
            throw new BizException(ErrorCode.A0001, "该检查已预约，请勿重复预约");
        }
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<RisRequest>()
                .eq(RisRequest::getId, id)
                .eq(RisRequest::getStatus, 10)
                .set(RisRequest::getStatus, 20)
                .set(RisRequest::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0008, "申请状态已变化，请刷新后重试");
        }
        return appointment.getId();
    }

    /** 开始检查（R-04）：20 → 30 */
    @Transactional
    public void start(Long id) {
        // 行锁：与退费联动作废互斥，避免“已退费仍可开始检查”
        RisRequest request = requireRequestForUpdate(id);
        if (request.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "检查未预约，不能开始");
        }
        RisAppointment appointment = appointmentMapper.selectOne(new LambdaQueryWrapper<RisAppointment>()
                .eq(RisAppointment::getRequestId, id).last("LIMIT 1"));
        if (appointment != null) {
            appointment.setStatus(2);
            appointmentMapper.updateById(appointment);
        }
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<RisRequest>()
                .eq(RisRequest::getId, id)
                .eq(RisRequest::getStatus, 20)
                .set(RisRequest::getStatus, 30)
                .set(RisRequest::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0008, "申请状态已变化，请刷新后重试");
        }
    }

    /** 影像归档（R-05）：检查中归档；fetch=true 走 Mock 设备通道 */
    @Transactional
    public String archive(Long id, ImageArchiveRequest req) {
        RisRequest request = requireRequest(id);
        if (request.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "检查未开始，不能归档影像");
        }
        RisImage image = imageMapper.selectOne(new LambdaQueryWrapper<RisImage>()
                .eq(RisImage::getRequestId, id).last("LIMIT 1"));
        int series;
        int count;
        String impression;
        String criticalSign;
        if (Boolean.TRUE.equals(req.getFetch())) {
            ImagingGateway.MockStudy study = imagingGateway.mockStudy(
                    request.getModality(), request.getBodyPart(), request.getRequestNo());
            series = study.seriesCount();
            count = study.imageCount();
            impression = study.impressionText();
            criticalSign = study.criticalSign();
        } else {
            series = req.getSeriesCount() == null ? 0 : req.getSeriesCount();
            count = req.getImageCount() == null ? 0 : req.getImageCount();
            // 零影像"归档"会生成一行空影像记录，使 finish/writeReport 的"已归档"行数校验通过，
            // 最终产出无影像依据的报告（R-05/R-07 闭环被绕过）
            if (series < 1 || count < 1) {
                throw new BizException(ErrorCode.A0001, "手工归档须填写序列数（≥1）与影像数（≥1）");
            }
            impression = req.getImpressionText();
            criticalSign = null;
        }
        if (image == null) {
            image = new RisImage();
            image.setStudyNo(idGenerator.next("IM"));
            image.setRequestId(id);
            image.setStatus(1);
        }
        image.setSeriesCount(series);
        image.setImageCount(count);
        image.setImpressionText(impression);
        if (image.getId() == null) {
            imageMapper.insert(image);
        } else {
            imageMapper.updateById(image);
        }
        return image.getStudyNo();
    }

    /** 检查完成确认（R-06）：影像归档后方可完成 */
    @Transactional
    public void finish(Long id) {
        RisRequest request = requireRequest(id);
        if (request.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "检查不在检查中状态");
        }
        // 校验影像数而非仅行数：历史零影像归档行不得再充当"已归档"
        if (!hasArchivedImages(id)) {
            throw new BizException(ErrorCode.A0001, "影像未归档，不能完成检查");
        }
    }

    /** 归档影像必须实际存在：有影像行且影像数 ≥1（零影像行 = 未归档） */
    private boolean hasArchivedImages(Long requestId) {
        RisImage image = imageMapper.selectOne(new LambdaQueryWrapper<RisImage>()
                .eq(RisImage::getRequestId, requestId).last("LIMIT 1"));
        return image != null && image.getImageCount() != null && image.getImageCount() > 0;
    }

    /** 报告书写（R-07）：检查中书写/驳回后修改重提 */
    @Transactional
    public String writeReport(ReportWriteRequest req) {
        RisRequest request = requireRequest(req.getRequestId());
        if (request.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "申请不在检查中状态，不能书写报告");
        }
        // 报告必须以影像为依据：finish 只在 UI 侧被调用，原 writeReport 可绕过影像归档直接出报告
        if (!hasArchivedImages(req.getRequestId())) {
            throw new BizException(ErrorCode.A0001, "影像未归档，不能书写报告");
        }
        RisReport report = reportMapper.selectOne(new LambdaQueryWrapper<RisReport>()
                .eq(RisReport::getRequestId, req.getRequestId()).last("LIMIT 1"));
        if (report == null) {
            report = new RisReport();
            report.setReportNo(idGenerator.next("XD"));
            report.setRequestId(req.getRequestId());
            report.setStatus(10);
        } else if (report.getStatus() != 10 && report.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "报告已发布，不可修改");
        }
        // 书写/重提人即最终书写人：双签校验（reviewer≠reporter）对重提后口径生效
        report.setReporterId(CurrentUser.id());
        report.setReportTime(LocalDateTime.now());
        report.setReviewerId(null);
        report.setReviewTime(null);
        report.setFinding(req.getFinding());
        report.setConclusion(req.getConclusion());
        report.setCriticalSign(req.getCriticalSign());
        report.setCriticalFlag(req.getCriticalSign() == null || req.getCriticalSign().isBlank() ? 0 : 1);
        report.setMutualFlag(req.getMutualFlag() == null ? 0 : req.getMutualFlag());
        report.setMutualNote(req.getMutualNote());
        report.setStatus(10);
        if (report.getId() == null) {
            reportMapper.insert(report);
        } else if (reportMapper.updateById(report) != 1) {
            throw new BizException(ErrorCode.A0008, "报告状态已变化，请刷新后重试");
        }
        return report.getReportNo();
    }

    /** 报告审核（R-08）：通过即发布（reviewer≠reporter）→ 危急值 + 申请已报告 */
    @Transactional
    public void reviewReport(Long reportId, ReportReviewRequest req) {
        RisReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ErrorCode.A0001, "报告不存在");
        }
        if (report.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "报告不在书写中状态");
        }
        if (!Boolean.TRUE.equals(req.getApproved())) {
            // 一百一十二轮 B09：驳回理由必填（原落成 "reason":"null" 留痕失真）+ 行数断言对齐通过分支
            if (req.getReason() == null || req.getReason().isBlank()) {
                throw new BizException(ErrorCode.A0001, "驳回必须填写理由");
            }
            report.setStatus(30);
            if (reportMapper.updateById(report) != 1) {
                throw new BizException(ErrorCode.A0008, "报告状态已变化，请刷新后重试");
            }
            pltService.recordEvent("ris.report.rejected", report.getReportNo(),
                    "{\"reason\":\"" + escapeJson(req.getReason()) + "\"}");
            return;
        }
        Long reviewer = CurrentUser.id();
        if (reviewer.equals(report.getReporterId())) {
            throw new BizException(ErrorCode.A0001, "报告审核须第二签名人，不得自审自签");
        }
        report.setStatus(20);
        report.setReviewerId(reviewer);
        report.setReviewTime(LocalDateTime.now());
        if (reportMapper.updateById(report) != 1) {
            throw new BizException(ErrorCode.A0008, "报告状态已变化，请刷新后重试");
        }
        RisRequest request = requireRequest(report.getRequestId());
        if (request.getStatus() == 30) {
            request.setStatus(40);
            requestMapper.updateById(request);
        }
        if (report.getCriticalFlag() != null && report.getCriticalFlag() == 1
                && report.getCriticalSign() != null && !report.getCriticalSign().isBlank()) {
            alertService.createSource2(request.getId(), report.getId(), request.getPatientId(),
                    request.getAdmissionId(), modalityName(request.getModality()), report.getCriticalSign());
        }
        pltService.recordEvent("ris.report.published", report.getReportNo(), "{}");
        // 门诊检查闭环：报告发布回写一期检查申请"已执行"（四期二批）
        try {
            com.his.modules.clinic.entity.CliExamApplication exam = findOutpatientExam(request.getId());
            if (exam != null && exam.getStatus() != null && exam.getStatus() == 20) {
                exam.setStatus(30);
                clinicExamApplicationMapper.updateById(exam);
            }
        } catch (Exception e) {
            log.warn("门诊检查回写失败 ris_request {}: {}", request.getId(), e.getMessage());
        }
    }

    /** 报告分页（R-09）：支持按住院/患者过滤（经申请单关联） */
    public PageResult<RisReport> reportPage(RisRequestQuery query) {
        Integer status = query.getStatus();
        if (!canViewDraftReport()) {
            // 普通查询者只能看已发布报告：草稿含未审核结论，提前可见可能被当成最终诊断
            if (status != null && status != 20) {
                return PageResult.of(new Page<>(query.getPageNum(), query.getPageSize()));
            }
            status = 20;
        }
        List<Long> requestIds = null;
        if (query.getAdmissionId() != null || query.getPatientId() != null) {
            List<RisRequest> requests = requestMapper.selectList(new LambdaQueryWrapper<RisRequest>()
                    .eq(query.getAdmissionId() != null, RisRequest::getAdmissionId, query.getAdmissionId())
                    .eq(query.getPatientId() != null, RisRequest::getPatientId, query.getPatientId()));
            requestIds = requests.stream().map(RisRequest::getId).toList();
            if (requestIds.isEmpty()) {
                return PageResult.of(new Page<>(query.getPageNum(), query.getPageSize()));
            }
        }
        Page<RisReport> page = reportMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<RisReport>()
                        .in(requestIds != null, RisReport::getRequestId, requestIds)
                        .eq(status != null, RisReport::getStatus, status)
                        .orderByDesc(RisReport::getId));
        return PageResult.of(page);
    }

    /** 报告详情（R-09，含申请与影像） */
    public Map<String, Object> reportDetail(Long requestId) {
        RisReport report = reportMapper.selectOne(new LambdaQueryWrapper<RisReport>()
                .eq(RisReport::getRequestId, requestId).last("LIMIT 1"));
        if (report == null) {
            throw new BizException(ErrorCode.A0001, "报告不存在");
        }
        if (report.getStatus() != null && report.getStatus() != 20 && !canViewDraftReport()) {
            throw new BizException(ErrorCode.A0003, "报告尚未审核发布");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report", report);
        result.put("request", requestMapper.selectById(requestId));
        result.put("image", imageMapper.selectOne(new LambdaQueryWrapper<RisImage>()
                .eq(RisImage::getRequestId, requestId).last("LIMIT 1")));
        return result;
    }

    /** 报告书写/审核者可见草稿（技师等只读角色只能看已发布） */
    private boolean canViewDraftReport() {
        java.util.Set<String> perms = CurrentUser.get().getPermissions();
        return perms != null && (perms.contains("ris:report:write") || perms.contains("ris:report:review"));
    }

    /** 设备列表（R-10） */
    public List<RisDevice> devices() {
        return deviceMapper.selectList(new LambdaQueryWrapper<RisDevice>()
                .orderByAsc(RisDevice::getDeviceNo));
    }

    private RisRequest requireRequest(Long id) {
        RisRequest request = requestMapper.selectById(id);
        if (request == null) {
            throw new BizException(ErrorCode.A0001, "检查申请不存在");
        }
        return request;
    }

    private RisRequest requireRequestForUpdate(Long id) {
        RisRequest request = requestMapper.selectByIdForUpdate(id);
        if (request == null) {
            throw new BizException(ErrorCode.A0001, "检查申请不存在");
        }
        return request;
    }

    /** 按医嘱项目名推断检查大类（名称含 CT/MR 识别，其余归 DR） */
    private int inferModality(String itemName) {
        String name = itemName == null ? "" : itemName;
        if (name.contains("CT") || name.contains("ct")) {
            return 2;
        }
        if (name.contains("MR") || name.contains("磁共振") || name.contains("核磁")) {
            return 3;
        }
        return 1;
    }

    private String modalityName(Integer modality) {
        if (modality == null) {
            return "影像检查";
        }
        return switch (modality) {
            case 2 -> "CT检查";
            case 3 -> "MR检查";
            case 4 -> "超声检查";
            case 5 -> "心电检查";
            default -> "DR检查";
        };
    }

    /** 事件载荷 JSON 字符串转义（五十六轮：原私有实现把 " 替换为 ' 篡改留痕数据，统一委托共享工具保真转义） */
    private String escapeJson(String s) {
        return com.his.infrastructure.util.JsonEscapeUtil.escape(s);
    }
}
