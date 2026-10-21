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
        if (req.getDeptId() == null) {
            throw new BizException(ErrorCode.A0001, "受邀科室不能为空");
        }
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new BizException(ErrorCode.A0001, "会诊目的不能为空");
        }
        if (req.getReason().length() > 512) {
            throw new BizException(ErrorCode.A0001, "会诊目的最长 512 字");
        }
        if (req.getUrgent() != null && (req.getUrgent() < 0 || req.getUrgent() > 1)) {
            throw new BizException(ErrorCode.A0001, "急会诊标识取值 0/1");
        }
        // 裸实体入参：主键与流程字段必须由服务端维护，避免伪造接受/意见留痕
        req.setId(null);
        req.setConsultDoctorId(null);
        req.setAcceptTime(null);
        req.setOpinion(null);
        req.setOpinionTime(null);
        req.setVersion(0);
        req.setCreatedBy(null);
        req.setCreatedAt(null);
        req.setUpdatedAt(null);
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
            // 患者一致性：原门诊分支既不校验也不回填 patientId，可给 A 患者就诊挂 B 患者会诊
            if (opVisit.getPatientId() != null) {
                if (req.getPatientId() != null && !opVisit.getPatientId().equals(req.getPatientId())) {
                    throw new BizException(ErrorCode.A0001, "患者与门诊就诊不匹配，禁止跨患者挂会诊");
                }
                req.setPatientId(opVisit.getPatientId());
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

    /** 接受（T-02）：10 → 20（受邀科室医生；管理员代接受豁免） */
    @Transactional
    public void accept(Long id) {
        CntRequest req = requireRequest(id);
        if (req.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "会诊不在待接受状态");
        }
        // 跨科室抢单：接受者即 consultDoctorId，一旦被非受邀科室抢走，
        // 受邀科室医生再也无法填写意见（complete 仅认接受者/管理员）
        if (!CurrentUser.get().getRoleCodes().contains("ADMIN")) {
            var doctor = basedataAppService.getDoctorByUserId(CurrentUser.id());
            if (req.getDeptId() == null || doctor == null
                    || !req.getDeptId().equals(doctor.getDeptId())) {
                throw new BizException(ErrorCode.A0003, "仅受邀科室医生可接受会诊");
            }
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
        // 一百零七轮 C7：空意见兜底拦截（Controller 已有 @RequestParam 前置校验）
        if (opinion == null || opinion.isBlank()) {
            throw new BizException(ErrorCode.A0001, "会诊意见不能为空");
        }
        if (opinion.length() > 1024) {
            throw new BizException(ErrorCode.A0001, "会诊意见最长 1024 字");
        }
        // 会诊意见须由受邀医生（=接受者）填写（八十八轮 IDOR 审计 P1-3）
        if (req.getConsultDoctorId() != null && !req.getConsultDoctorId().equals(CurrentUser.id())
                && !com.his.infrastructure.security.CurrentUser.get().getRoleCodes().contains("ADMIN")) {
            throw new BizException(ErrorCode.A0003, "会诊意见仅受邀医生可填写");
        }
        req.setStatus(30);
        req.setOpinion(opinion.trim());
        req.setOpinionTime(LocalDateTime.now());
        // 一百零七轮 C7：接受者即会诊医师——管理员代完成不得覆盖真实会诊医师留痕
        if (req.getConsultDoctorId() == null) {
            req.setConsultDoctorId(CurrentUser.id());
        }
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
