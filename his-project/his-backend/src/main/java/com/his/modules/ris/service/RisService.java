package com.his.modules.ris.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
@RequiredArgsConstructor
public class RisService {
    private final RisRequestMapper requestMapper;
    private final RisDeviceMapper deviceMapper;
    private final RisAppointmentMapper appointmentMapper;
    private final RisImageMapper imageMapper;
    private final RisReportMapper reportMapper;
    private final ImagingGateway imagingGateway;
    private final AlertService alertService;
    private final PltService pltService;
    private final IdGenerator idGenerator;

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
            request.setStatus(50);
            requestMapper.updateById(request);
            pltService.recordEvent("ris.request.voided", request.getRequestNo(),
                    "{\"orderId\":" + orderId + "}");
        }
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
        RisRequest request = requireRequest(id);
        if (request.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "申请不在待预约状态");
        }
        RisDevice device = deviceMapper.selectById(req.getDeviceId());
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
        RisAppointment appointment = new RisAppointment();
        appointment.setRequestId(id);
        appointment.setDeviceId(req.getDeviceId());
        appointment.setApptTime(req.getApptTime());
        appointment.setStatus(1);
        appointmentMapper.insert(appointment);
        request.setStatus(20);
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "申请状态已变化，请刷新后重试");
        }
        return appointment.getId();
    }

    /** 开始检查（R-04）：20 → 30 */
    @Transactional
    public void start(Long id) {
        RisRequest request = requireRequest(id);
        if (request.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "检查未预约，不能开始");
        }
        RisAppointment appointment = appointmentMapper.selectOne(new LambdaQueryWrapper<RisAppointment>()
                .eq(RisAppointment::getRequestId, id).last("LIMIT 1"));
        if (appointment != null) {
            appointment.setStatus(2);
            appointmentMapper.updateById(appointment);
        }
        request.setStatus(30);
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "申请状态已变化，请刷新后重试");
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
        Long images = imageMapper.selectCount(new LambdaQueryWrapper<RisImage>()
                .eq(RisImage::getRequestId, id));
        if (images == null || images == 0) {
            throw new BizException(ErrorCode.A0001, "影像未归档，不能完成检查");
        }
    }

    /** 报告书写（R-07）：检查中书写/驳回后修改重提 */
    @Transactional
    public String writeReport(ReportWriteRequest req) {
        RisRequest request = requireRequest(req.getRequestId());
        if (request.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "申请不在检查中状态，不能书写报告");
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
        report.setStatus(10);
        if (report.getId() == null) {
            reportMapper.insert(report);
        } else if (reportMapper.updateById(report) != 1) {
            throw new BizException(ErrorCode.A0001, "报告状态已变化，请刷新后重试");
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
            report.setStatus(30);
            reportMapper.updateById(report);
            pltService.recordEvent("ris.report.rejected", report.getReportNo(),
                    "{\"reason\":\"" + (req.getReason() == null ? "" : req.getReason().replace("\"", "'")) + "\"}");
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
            throw new BizException(ErrorCode.A0001, "报告状态已变化，请刷新后重试");
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
    }

    /** 报告分页（R-09） */
    public PageResult<RisReport> reportPage(RisRequestQuery query) {
        Page<RisReport> page = reportMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<RisReport>()
                        .eq(query.getStatus() != null, RisReport::getStatus, query.getStatus())
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report", report);
        result.put("request", requestMapper.selectById(requestId));
        result.put("image", imageMapper.selectOne(new LambdaQueryWrapper<RisImage>()
                .eq(RisImage::getRequestId, requestId).last("LIMIT 1")));
        return result;
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
}
