package com.his.modules.cnt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
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

    /** 会诊申请（T-01）：住院/门诊二选一 */
    @Transactional
    public String create(CntRequest req) {
        if (req.getAdmissionId() == null && req.getVisitId() == null) {
            throw new BizException(ErrorCode.A0001, "住院或门诊就诊至少关联一项");
        }
        if (req.getAdmissionId() != null) {
            inpAppService.requireInHospital(req.getAdmissionId());
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
                        .orderByAsc(CntRequest::getStatus).orderByDesc(CntRequest::getId));
        return PageResult.of(page);
    }

    /** 接受（T-02）：10 → 20（受邀医生） */
    @Transactional
    public void accept(Long id) {
        CntRequest req = requireRequest(id);
        if (req.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "会诊不在待接受状态");
        }
        req.setStatus(20);
        req.setAcceptTime(LocalDateTime.now());
        if (requestMapper.updateById(req) != 1) {
            throw new BizException(ErrorCode.A0001, "会诊状态已变化，请刷新后重试");
        }
    }

    /** 完成意见（T-03）：20 → 30（会诊医生） */
    @Transactional
    public void complete(Long id, String opinion) {
        CntRequest req = requireRequest(id);
        if (req.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "会诊未接受，不能完成");
        }
        req.setStatus(30);
        req.setOpinion(opinion);
        req.setOpinionTime(LocalDateTime.now());
        req.setConsultDoctorId(CurrentUser.id());
        if (requestMapper.updateById(req) != 1) {
            throw new BizException(ErrorCode.A0001, "会诊状态已变化，请刷新后重试");
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
