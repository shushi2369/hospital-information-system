package com.his.modules.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageQuery;
import com.his.common.PageResult;
import java.util.Map;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.alert.entity.AlertCritical;
import com.his.modules.alert.mapper.AlertCriticalMapper;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.DoctorDTO;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.lis.entity.LisRequest;
import com.his.modules.lis.entity.LisResult;
import com.his.modules.lis.mapper.LisRequestMapper;
import com.his.modules.plt.service.PltService;
import com.his.modules.ris.mapper.RisRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 危急值闭环服务（《10》§4.2 状态机）：待处理 → 已通知 → 已确认 → 已闭环。
 * 数据范围：住院按责任医生（admission.doctor_id）、门诊按开单医生（申请单 doctor_id），管理员豁免。
 */
@Service
@RequiredArgsConstructor
public class AlertService {
    private final AlertCriticalMapper alertMapper;
    private final InpAppService inpAppService;
    private final com.his.modules.basedata.app.BasedataAppService basedataAppService;
    private final PltService pltService;
    private final IdGenerator idGenerator;
    private final com.his.modules.system.app.SystemAppService systemAppService;
    private final LisRequestMapper lisRequestMapper;
    private final RisRequestMapper risRequestMapper;

    /** 结果危急时生成危急值（lis 调用） */
    @Transactional
    public void create(LisRequest request, Long resultId, String itemName, String value) {
        AlertCritical alert = new AlertCritical();
        alert.setAlertNo(idGenerator.next("WJ"));
        alert.setSource(1);
        alert.setRequestId(request.getId());
        alert.setResultId(resultId);
        alert.setPatientId(request.getPatientId());
        alert.setAdmissionId(request.getAdmissionId());
        alert.setItemName(itemName);
        alert.setCriticalValue(value);
        alert.setStatus(10);
        alertMapper.insert(alert);
        pltService.recordEvent("alert.created", alert.getAlertNo(),
                "{\"item\":\"" + com.his.infrastructure.util.JsonEscapeUtil.escape(itemName) + "\"}");
    }

    /** 影像危急征象生成危急值（ris 调用，source=2 PACS，《16》§3.16） */
    @Transactional
    public void createSource2(Long requestId, Long resultId, Long patientId, Long admissionId,
                              String itemName, String value) {
        AlertCritical alert = new AlertCritical();
        alert.setAlertNo(idGenerator.next("WJ"));
        alert.setSource(2);
        alert.setRequestId(requestId);
        alert.setResultId(resultId);
        alert.setPatientId(patientId);
        alert.setAdmissionId(admissionId);
        alert.setItemName(itemName);
        alert.setCriticalValue(value);
        alert.setStatus(10);
        alertMapper.insert(alert);
        pltService.recordEvent("alert.created", alert.getAlertNo(),
                "{\"source\":2,\"item\":\"" + com.his.infrastructure.util.JsonEscapeUtil.escape(itemName) + "\"}");
    }

    /** 危急值分页（W-01）：医生站"待我确认"——医生仅见本人管床/开单患者的危急值；
     *  检验/影像/护理/管理员/审计员保留全院工作台视图。 */
    public PageResult<AlertCritical> page(Integer status, PageQuery query) {
        LambdaQueryWrapper<AlertCritical> wrapper = new LambdaQueryWrapper<AlertCritical>()
                .eq(status != null, AlertCritical::getStatus, status)
                .orderByAsc(AlertCritical::getStatus).orderByDesc(AlertCritical::getId);
        var roles = CurrentUser.get().getRoleCodes();
        boolean worklist = roles.contains("ADMIN") || roles.contains("AUDITOR")
                || roles.contains("NURSE") || roles.contains("LIS_USER") || roles.contains("RIS_USER");
        if (!worklist && roles.contains("DOCTOR")) {
            DoctorDTO doctor = basedataAppService.getDoctorByUserId(CurrentUser.id());
            if (doctor == null) {
                return PageResult.of(new Page<>(query.getPageNum(), query.getPageSize()));
            }
            Long did = doctor.getId();
            // 住院按责任医生；门诊按来源申请单开单医生（lis/ris 请求 doctor_id = bas_doctor.id）
            wrapper.and(w -> w
                    .inSql(AlertCritical::getAdmissionId,
                            "SELECT id FROM inp_admission WHERE doctor_id = " + did)
                    .or(x -> x.isNull(AlertCritical::getAdmissionId)
                            .and(y -> y.nested(z -> z.eq(AlertCritical::getSource, 1)
                                            .inSql(AlertCritical::getRequestId,
                                                    "SELECT id FROM lis_request WHERE doctor_id = " + did))
                                    .or(z -> z.eq(AlertCritical::getSource, 2)
                                            .inSql(AlertCritical::getRequestId,
                                                    "SELECT id FROM ris_request WHERE doctor_id = " + did)))));
        }
        Page<AlertCritical> page = alertMapper.selectPage(query.toPage(), wrapper);
        // 通知人/确认人姓名回填（一百零二轮裸 ID 清查 #1）
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        for (AlertCritical a : page.getRecords()) {
            if (a.getNotifiedNurse() != null) userIds.add(a.getNotifiedNurse());
            if (a.getConfirmedDoctor() != null) userIds.add(a.getConfirmedDoctor());
        }
        Map<Long, String> names = systemAppService.getUsernameMap(userIds);
        for (AlertCritical a : page.getRecords()) {
            a.setNotifiedNurseName(names.get(a.getNotifiedNurse()));
            a.setConfirmedDoctorName(names.get(a.getConfirmedDoctor()));
        }
        return PageResult.of(page);
    }

    /** 通知登记（W-02，技师/护士）：10 → 20 */
    @Transactional
    public void notify(Long alertId) {
        AlertCritical alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BizException(ErrorCode.A0001, "危急值不存在");
        }
        if (alert.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "危急值不在待处理状态");
        }
        int updated = alertMapper.update(null, new LambdaUpdateWrapper<AlertCritical>()
                .eq(AlertCritical::getId, alertId)
                .eq(AlertCritical::getStatus, 10)
                .set(AlertCritical::getStatus, 20)
                .set(AlertCritical::getNotifiedNurse, CurrentUser.id())
                .set(AlertCritical::getNotifiedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0008, "危急值状态已变化，请刷新后重试");
        }
    }

    /** 医生确认（W-03）：20 → 30，仅本人管床（管理员豁免） */
    @Transactional
    public void confirm(Long alertId) {
        AlertCritical alert = requireAlert(alertId);
        if (alert.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "危急值未完成通知登记");
        }
        checkDoctorScope(alert);
        int updated = alertMapper.update(null, new LambdaUpdateWrapper<AlertCritical>()
                .eq(AlertCritical::getId, alertId)
                .eq(AlertCritical::getStatus, 20)
                .set(AlertCritical::getStatus, 30)
                .set(AlertCritical::getConfirmedDoctor, CurrentUser.id())
                .set(AlertCritical::getConfirmedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0008, "危急值状态已变化，请刷新后重试");
        }
    }

    /** 处置闭环（W-04）：30 → 40 */
    @Transactional
    public void close(Long alertId, String handleNote) {
        // 一百一十四轮 #11：闭环处置意见必填（与 bb_adverse NOT NULL 口径对齐，闭环留痕不缺失）
        if (handleNote == null || handleNote.isBlank()) {
            throw new BizException(ErrorCode.A0001, "请填写处置意见后再闭环");
        }
        if (handleNote.length() > 256) {
            throw new BizException(ErrorCode.A0001, "处置意见最长 256 字");
        }
        AlertCritical alert = requireAlert(alertId);
        if (alert.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "危急值未确认，不能闭环");
        }
        checkDoctorScope(alert);
        int updated = alertMapper.update(null, new LambdaUpdateWrapper<AlertCritical>()
                .eq(AlertCritical::getId, alertId)
                .eq(AlertCritical::getStatus, 30)
                .set(AlertCritical::getStatus, 40)
                .set(AlertCritical::getHandleNote, handleNote));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0008, "危急值状态已变化，请刷新后重试");
        }
        pltService.recordEvent("alert.closed", alert.getAlertNo(), "{}");
    }

    private void checkDoctorScope(AlertCritical alert) {
        com.his.infrastructure.security.LoginUser user = CurrentUser.get();
        if (user.getRoleCodes().contains("ADMIN")) {
            return;
        }
        var doctor = basedataAppService.getDoctorByUserId(user.getUserId());
        if (doctor == null) {
            throw new BizException(ErrorCode.A0003, "仅开单/责任医生可处理本人患者的危急值");
        }
        if (alert.getAdmissionId() != null) {
            InpAdmission admission = inpAppService.getAdmission(alert.getAdmissionId());
            if (admission == null || !admission.getDoctorId().equals(doctor.getId())) {
                throw new BizException(ErrorCode.A0003, "仅责任医生可确认本人管床患者的危急值");
            }
            return;
        }
        // 门诊危急值无住院记录，按来源申请单的开单医生界定；来源未登记时拒绝而非放行
        Long orderDoctorId = null;
        if (alert.getSource() != null && alert.getSource() == 2) {
            var request = risRequestMapper.selectById(alert.getRequestId());
            orderDoctorId = request == null ? null : request.getDoctorId();
        } else {
            var request = lisRequestMapper.selectById(alert.getRequestId());
            orderDoctorId = request == null ? null : request.getDoctorId();
        }
        if (!doctor.getId().equals(orderDoctorId)) {
            throw new BizException(ErrorCode.A0003, "仅开单医生可确认本人患者的危急值");
        }
    }

    /** 一百一十四轮 #7b：危急值闭环率（口径：人工闭环(40) / 全部），按天维度由前端过滤 createdAt。
     *  一百一十六轮复核【7】：排除"结果重录，本危急值随重录作废"的系统关闭行——
     *  自动关断计入分子会让重录越多闭环率越好看 */
    public java.util.Map<String, Object> stats() {
        long closed = alertMapper.selectCount(new LambdaQueryWrapper<AlertCritical>()
                .eq(AlertCritical::getStatus, 40)
                .notLike(AlertCritical::getHandleNote, "结果重录，本危急值随重录作废"));
        long total = alertMapper.selectCount(new LambdaQueryWrapper<AlertCritical>());
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", total);
        result.put("closed", closed);
        result.put("inFlight", total - closed);
        result.put("closureRate", total == 0 ? "0%" :
                java.math.BigDecimal.valueOf(closed * 100).divide(java.math.BigDecimal.valueOf(total), 1,
                        java.math.RoundingMode.HALF_UP) + "%");
        return result;
    }

    private AlertCritical requireAlert(Long alertId) {
        AlertCritical alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BizException(ErrorCode.A0001, "危急值不存在");
        }
        return alert;
    }

}
