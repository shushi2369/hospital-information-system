package com.his.modules.cnt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import java.util.Map;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.cnt.entity.CntRequest;
import com.his.modules.cnt.mapper.CntRequestMapper;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 会诊服务（四期三批）：申请 → 接受 → 完成（会诊意见）。 */
@Service
@RequiredArgsConstructor
public class CntService {
    private final CntRequestMapper requestMapper;
    private final InpAppService inpAppService;
    private final PltService pltService;
    private final IdGenerator idGenerator;
    private final com.his.modules.basedata.app.BasedataAppService basedataAppService;
    private final com.his.modules.patient.app.PatientAppService patientAppService;
    private final com.his.modules.clinic.mapper.CliVisitMapper clinicVisitMapper;
    private final com.his.modules.system.app.SystemAppService systemAppService;

    /** 会诊申请（T-01）：住院/门诊二选一 */
    @Transactional
    public String create(CntRequest req) {
        if (req.getAdmissionId() == null && req.getVisitId() == null) {
            throw new BizException(ErrorCode.A0001, "住院或门诊就诊至少关联一项");
        }
        if (req.getAdmissionId() != null) {
            var adm = inpAppService.requireInHospital(req.getAdmissionId());
            // C3（一百零七轮走查）：患者与住院登记一致性校验——防跨患者挂会诊
            if (adm.getPatientId() != null && req.getPatientId() != null
                    && !adm.getPatientId().equals(req.getPatientId())) {
                throw new BizException(ErrorCode.A0001, "患者与住院登记不匹配，禁止跨患者挂会诊");
            }
            if (adm.getPatientId() != null) {
                req.setPatientId(adm.getPatientId());
            }
        }
        if (req.getVisitId() != null) {
            // C4（一百零七轮走查）：门诊 visitId 存在性校验——原代码无任何校验
            com.his.modules.clinic.entity.CliVisit opVisit = clinicVisitMapper.selectById(req.getVisitId());
            if (opVisit == null) {
                throw new BizException(ErrorCode.A0001, "门诊就诊不存在");
            }
        }
        req.setReqNo(idGenerator.next("HZ"));
        req.setPatientId(req.getPatientId());
        req.setApplicantId(CurrentUser.id());
        req.setUrgent(req.getUrgent() == null ? 0 : req.getUrgent());
        req.setStatus(10);
        requestMapper.insert(req);
        pltService.recordEvent("cnt.request.created", req.getReqNo(),
                "{\"urgent\":" + req.getUrgent() + "}");
        return req.getReqNo();
    }

    /** 会诊列表（T-04）：按状态/患者过滤 */
    public PageResult<CntRequest> page(com.his.common.PageQuery query, Long patientId, Integer status) {
        Page<CntRequest> page = requestMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<CntRequest>()
                        .eq(patientId != null, CntRequest::getPatientId, patientId)
                        .eq(status != null, CntRequest::getStatus, status)
                        .orderByDesc(CntRequest::getUrgent)
                .orderByAsc(CntRequest::getStatus).orderByDesc(CntRequest::getId));
        // 科室名/医师名批量回填（一百轮浏览器走查：裸 ID 列对齐九十二轮幽灵字段修法）
        java.util.Set<Long> doctorIds = new java.util.HashSet<>();
        for (CntRequest r : page.getRecords()) {
            if (r.getConsultDoctorId() != null) doctorIds.add(r.getConsultDoctorId());
        }
        Map<Long, String> doctorNames = systemAppService.getUsernameMap(doctorIds);
        Map<Long, ? extends com.his.modules.basedata.app.DepartmentDTO> depts =
                basedataAppService.departmentMap();
        for (CntRequest r : page.getRecords()) {
            var d = depts.get(r.getDeptId());
            r.setDeptName(d == null ? null : d.getDeptName());
            r.setConsultDoctorName(doctorNames.get(r.getConsultDoctorId()));
        }
        com.his.infrastructure.util.PatientNameBackfill.fill(page.getRecords(), patientAppService);
        return PageResult.of(page);
    }

    /** 接受（T-02）：10 → 20（受邀医生） */
    @Transactional
    public void accept(Long id) {
        CntRequest req = requireRequest(id);
        if (req.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "会诊不在待接受状态");
        }
        // 接受者即受邀会诊医生（八十八轮 IDOR 审计 P1-3：完成时须同一医生或管理员）
        req.setConsultDoctorId(CurrentUser.id());
        req.setStatus(20);
        req.setAcceptTime(LocalDateTime.now());
        if (requestMapper.updateById(req) != 1) {
            throw new BizException(ErrorCode.A0008, "会诊状态已变化，请刷新后重试");
        }
    }

    /** 完成意见（T-03）：20 → 30（会诊医生） */
    @Transactional
    public void complete(Long id, String opinion) {
        CntRequest req = requireRequest(id);
        if (req.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "会诊未接受，不能完成");
        }
        // 会诊意见须由受邀医生（=接受者）填写（八十八轮 IDOR 审计 P1-3）
        if (req.getConsultDoctorId() != null && !req.getConsultDoctorId().equals(CurrentUser.id())
                && !com.his.infrastructure.security.CurrentUser.get().getRoleCodes().contains("ADMIN")) {
            throw new BizException(ErrorCode.A0003, "会诊意见仅受邀医生可填写");
        }
        req.setStatus(30);
        req.setOpinion(opinion);
        req.setOpinionTime(LocalDateTime.now());
        req.setConsultDoctorId(CurrentUser.id());
        if (requestMapper.updateById(req) != 1) {
            throw new BizException(ErrorCode.A0008, "会诊状态已变化，请刷新后重试");
        }
        pltService.recordEvent("cnt.completed", req.getReqNo(), "{}");
    }

    private CntRequest requireRequest(Long id) {
        CntRequest req = requestMapper.selectById(id);
        if (req == null) {
            throw new BizException(ErrorCode.A0001, "会诊申请不存在");
        }
        return req;
    }
}
