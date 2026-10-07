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
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 危急值闭环服务（《10》§4.2 状态机）：待处理 → 已通知 → 已确认 → 已闭环。
 * 数据范围：医生确认仅限本人管床（admission.doctor_id），管理员豁免。
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

    /** 危急值分页（W-01） */
    public PageResult<AlertCritical> page(Integer status, PageQuery query) {
        Page<AlertCritical> page = alertMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<AlertCritical>()
                        .eq(status != null, AlertCritical::getStatus, status)
                        .orderByAsc(AlertCritical::getStatus).orderByDesc(AlertCritical::getId));
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
        InpAdmission admission = inpAppService.getAdmission(alert.getAdmissionId());
        if (doctor == null || admission == null
                || !admission.getDoctorId().equals(doctor.getId())) {
            throw new BizException(ErrorCode.A0003, "仅责任医生可确认本人管床患者的危急值");
        }
    }

    private AlertCritical requireAlert(Long alertId) {
        AlertCritical alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BizException(ErrorCode.A0001, "危急值不存在");
        }
        return alert;
    }

}
