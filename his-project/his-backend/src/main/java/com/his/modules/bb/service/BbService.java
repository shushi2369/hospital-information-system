package com.his.modules.bb.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.bb.dto.*;
import com.his.modules.bb.entity.*;
import com.his.modules.bb.mapper.*;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 输血闭环服务（《21》§2.1）：申请 → 审核 → 交叉配血 → 发血 → 床边双签输注 → 闭环。
 * 三道服务端硬门禁（患者安全）：
 * ①配血不相容 → 禁止发血（阻断级）；②发血须取血护士签收；③输注开始须床边双人双签（不同人）。
 */
@Service
@RequiredArgsConstructor
public class BbService {
    private final BbBloodBagMapper bagMapper;
    private final BbRequestMapper requestMapper;
    private final BbCrossMatchMapper crossMapper;
    private final BbIssueMapper issueMapper;
    private final BbTransfusionMapper transfusionMapper;
    private final BbAdverseMapper adverseMapper;
    private final InpAppService inpAppService;
    private final com.his.modules.system.app.SystemAppService systemAppService;
    private final PltService pltService;
    private final IdGenerator idGenerator;

    /** 血袋分页（B-01 查询侧）；bagNo 用于按单号精确定位（效期升序分页下新袋在尾部，跨页检索不可靠） */
    public PageResult<BbBloodBag> bagPage(com.his.common.PageQuery query, Integer bloodType, Integer component, Integer status, String bagNo) {
        Page<BbBloodBag> page = bagMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<BbBloodBag>()
                        .eq(bloodType != null, BbBloodBag::getBloodType, bloodType)
                        .eq(component != null, BbBloodBag::getComponent, component)
                        .eq(status != null, BbBloodBag::getStatus, status)
                        .eq(bagNo != null && !bagNo.isBlank(), BbBloodBag::getBagNo, bagNo)
                        .orderByAsc(BbBloodBag::getExpireDate));
        return PageResult.of(page);
    }

    /** 可用血袋（B-02）：在库且未过期，效期升序（先到期先用） */
    public List<BbBloodBag> availableBags(Integer bloodType, Integer component) {
        return bagMapper.selectList(new LambdaQueryWrapper<BbBloodBag>()
                .eq(BbBloodBag::getStatus, 1)
                .gt(BbBloodBag::getExpireDate, LocalDate.now())
                .eq(bloodType != null, BbBloodBag::getBloodType, bloodType)
                .eq(component != null, BbBloodBag::getComponent, component)
                .orderByAsc(BbBloodBag::getExpireDate));
    }

    /** 血袋入库（B-01） */
    @Transactional
    public String createBag(BbBagRequest req) {
        if (!req.getExpireDate().isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.A0001, "血袋已过期，禁止入库");
        }
        BbBloodBag bag = new BbBloodBag();
        bag.setBagNo(req.getBagNo());
        bag.setBloodType(req.getBloodType());
        bag.setRh(req.getRh());
        bag.setComponent(req.getComponent());
        bag.setVolumeMl(req.getVolumeMl() == null ? 200 : req.getVolumeMl());
        bag.setBloodStation(req.getBloodStation());
        bag.setCollectDate(req.getCollectDate());
        bag.setExpireDate(req.getExpireDate());
        bag.setStatus(1);
        try {
            bagMapper.insert(bag);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.A0001, "血袋号已存在");
        }
        return bag.getBagNo();
    }

    /** 血袋报废（B-01） */
    @Transactional
    public void scrapBag(Long bagId) {
        BbBloodBag bag = requireBag(bagId);
        if (bag.getStatus() != 1) {
            throw new BizException(ErrorCode.A0001, "仅在线血袋可报废");
        }
        bag.setStatus(3);
        if (bagMapper.updateById(bag) != 1) {
            throw new BizException(ErrorCode.A0001, "血袋状态已变化，请刷新后重试");
        }
    }

    /** 用血申请（B-03） */
    @Transactional
    public String createRequest(BbRequestCreateRequest req) {
        var admission = inpAppService.requireInHospital(req.getAdmissionId());
        if (admission.getPatientId() == null || !admission.getPatientId().equals(req.getPatientId())) {
            throw new BizException(ErrorCode.A0001, "患者与住院登记不匹配，禁止跨患者申请用血");
        }
        BbRequest request = new BbRequest();
        request.setReqNo(idGenerator.next("XY"));
        request.setAdmissionId(req.getAdmissionId());
        request.setPatientId(req.getPatientId());
        request.setDoctorId(CurrentUser.id());
        request.setBloodType(req.getBloodType());
        request.setRh(req.getRh());
        request.setComponent(req.getComponent());
        request.setVolumeMl(req.getVolumeMl());
        request.setUsePurpose(req.getUsePurpose());
        request.setStatus(10);
        requestMapper.insert(request);
        pltService.recordEvent("bb.request.created", request.getReqNo(),
                "{\"admissionId\":" + req.getAdmissionId() + "}");
        return request.getReqNo();
    }

    /** 申请分页（B-04） */
    public PageResult<BbRequest> requestPage(BbRequestQuery query) {
        Page<BbRequest> page = requestMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<BbRequest>()
                        .eq(query.getAdmissionId() != null, BbRequest::getAdmissionId, query.getAdmissionId())
                        .eq(query.getPatientId() != null, BbRequest::getPatientId, query.getPatientId())
                        .eq(query.getStatus() != null, BbRequest::getStatus, query.getStatus())
                        .orderByAsc(BbRequest::getStatus).orderByDesc(BbRequest::getId));
        return PageResult.of(page);
    }

    /** 详情聚合（B-05）：申请 + 配血记录 + 发血 + 输血执行 + 不良反应 */
    public Map<String, Object> detail(Long id) {
        BbRequest request = requireRequest(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request", request);
        result.put("crossMatches", crossMapper.selectList(new LambdaQueryWrapper<BbCrossMatch>()
                .eq(BbCrossMatch::getRequestId, id).orderByDesc(BbCrossMatch::getId)));
        result.put("issues", issueMapper.selectList(new LambdaQueryWrapper<BbIssue>()
                .eq(BbIssue::getRequestId, id)));
        result.put("transfusions", transfusionMapper.selectList(new LambdaQueryWrapper<BbTransfusion>()
                .eq(BbTransfusion::getRequestId, id)));
        result.put("adverses", adverseMapper.selectList(new LambdaQueryWrapper<BbAdverse>()
                .eq(BbAdverse::getRequestId, id).orderByDesc(BbAdverse::getId)));
        return result;
    }

    /** 审核（B-06）：10 → 20 / 10 → 80 */
    @Transactional
    public void review(Long id, boolean approved, String note) {
        BbRequest request = requireRequest(id);
        if (request.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "申请不在待审核状态");
        }
        request.setStatus(approved ? 20 : 80);
        request.setReviewerId(CurrentUser.id());
        request.setReviewNote(note);
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "申请状态已变化，请刷新后重试");
        }
        pltService.recordEvent("bb.request.reviewed", request.getReqNo(),
                "{\"approved\":" + approved + "}");
    }

    /** 取消（B-07）：10/20 → 70 */
    @Transactional
    public void cancel(Long id) {
        BbRequest request = requireRequest(id);
        if (request.getStatus() != 10 && request.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "待审核/配血中状态才可取消");
        }
        request.setStatus(70);
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "申请状态已变化，请刷新后重试");
        }
    }

    /**
     * 交叉配血（B-08）：状态须 20；血袋须在库未过期且血型/成分与申请一致；
     * 相容 → 30（配血完成）；不相容 → 保持 20 可换袋重配。
     */
    @Transactional
    public Long crossMatch(Long id, CrossMatchRequest req) {
        BbRequest request = requireRequest(id);
        if (request.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "申请不在配血中状态");
        }
        BbBloodBag bag = requireBag(req.getBagId());
        if (bag.getStatus() != 1 || !bag.getExpireDate().isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.A0001, "血袋不在库或已过期");
        }
        if (!bag.getBloodType().equals(request.getBloodType())
                || !bag.getComponent().equals(request.getComponent())) {
            throw new BizException(ErrorCode.A0001, "血袋血型/成分与申请不匹配");
        }
        if (!bag.getRh().equals(request.getRh())) {
            throw new BizException(ErrorCode.A0001, "Rh 血型不匹配（Rh 阴性患者禁止配 Rh 阳性血）");
        }
        BbCrossMatch record = new BbCrossMatch();
        record.setRequestId(id);
        record.setBagId(req.getBagId());
        record.setCrossMethod(req.getCrossMethod());
        record.setCrossResult(req.getCrossResult());
        record.setMatcherId(CurrentUser.id());
        record.setMatchTime(LocalDateTime.now());
        record.setNote(req.getNote());
        crossMapper.insert(record);
        if (req.getCrossResult() == 1) {
            request.setStatus(30);
            if (requestMapper.updateById(request) != 1) {
                throw new BizException(ErrorCode.A0001, "申请状态已变化，请刷新后重试");
            }
        }
        pltService.recordEvent("bb.request.matched", request.getReqNo(),
                "{\"result\":" + req.getCrossResult() + ",\"bag\":\"" + com.his.infrastructure.util.JsonEscapeUtil.escape(bag.getBagNo()) + "\"}");
        return record.getId();
    }

    /**
     * 发血（B-09，门禁①②）：状态须 30；**目标血袋必须存在"相容"配血记录（不相容硬阻断）**；
     * 发血须取血护士签收；血袋置已发用；申请 40。
     */
    @Transactional
    public Long issue(Long id, IssueRequest req) {
        BbRequest request = requireRequest(id);
        if (request.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "申请未完成相容配血，不能发血");
        }
        BbBloodBag bag = requireBag(req.getBagId());
        // 门禁①：该血袋必须存在"相容"配血记录；不相容记录存在即硬阻断
        List<BbCrossMatch> matches = crossMapper.selectList(new LambdaQueryWrapper<BbCrossMatch>()
                .eq(BbCrossMatch::getRequestId, id)
                .eq(BbCrossMatch::getBagId, req.getBagId())
                .orderByDesc(BbCrossMatch::getId));
        if (matches.isEmpty()) {
            throw new BizException(ErrorCode.A0001, "该血袋未做交叉配血，禁止发血");
        }
        boolean incompatible = matches.stream().anyMatch(m -> m.getCrossResult() == 2);
        boolean compatible = matches.stream().anyMatch(m -> m.getCrossResult() == 1);
        if (incompatible) {
            throw new BizException(ErrorCode.A0001, "配血不相容，禁止发血（患者安全阻断）");
        }
        if (!compatible) {
            throw new BizException(ErrorCode.A0001, "该血袋无相容配血记录，禁止发血");
        }
        if (bag.getStatus() != 1) {
            throw new BizException(ErrorCode.A0001, "血袋不在库");
        }
        if (!bag.getExpireDate().isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.A0001, "血袋已过期，禁止发血");
        }
        if (systemAppService.getUsername(req.getReceiverId()) == null) {
            throw new BizException(ErrorCode.A0001, "取血护士不存在");
        }
        // 门禁②：取血护士签收
        BbIssue issue = new BbIssue();
        issue.setRequestId(id);
        issue.setBagId(req.getBagId());
        issue.setIssuerId(CurrentUser.id());
        issue.setReceiverId(req.getReceiverId());
        issue.setIssueTime(LocalDateTime.now());
        try {
            issueMapper.insert(issue);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.A0001, "该血袋已发血");
        }
        // 条件更新占袋（1→2）：不同申请并发发同一袋血时 uk_issue_req_bag 不拦跨申请，必须在此闸死
        int occupied = bagMapper.update(null, new LambdaUpdateWrapper<BbBloodBag>()
                .eq(BbBloodBag::getId, bag.getId())
                .eq(BbBloodBag::getStatus, 1)
                .set(BbBloodBag::getStatus, 2));
        if (occupied != 1) {
            throw new BizException(ErrorCode.A0001, "血袋已被并发发血，请刷新后重试");
        }
        request.setStatus(40);
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "申请状态已变化，请刷新后重试");
        }
        pltService.recordEvent("bb.request.issued", request.getReqNo(),
                "{\"bag\":\"" + com.his.infrastructure.util.JsonEscapeUtil.escape(bag.getBagNo()) + "\"}");
        return issue.getId();
    }

    /** 输血开始（B-10，门禁③）：状态 40；床边双人双签（不同人）；申请 50 */
    @Transactional
    public Long startTransfusion(Long id, TransfusionStartRequest req) {
        BbRequest request = requireRequest(id);
        if (request.getStatus() != 40) {
            throw new BizException(ErrorCode.A0001, "血液未发血，不能开始输注");
        }
        // 核对签 1 强制为当前登录执行护士（床边执行者即本人，防代签）
        if (req.getChecker1Id() != null && !req.getChecker1Id().equals(CurrentUser.id())) {
            throw new BizException(ErrorCode.A0001, "核对签 1 须为当前执行护士本人");
        }
        if (systemAppService.getUsername(req.getChecker2Id()) == null) {
            throw new BizException(ErrorCode.A0001, "床边核对签 2 不存在");
        }
        if (req.getChecker2Id().equals(CurrentUser.id())) {
            throw new BizException(ErrorCode.A0001, "床边核对须双人双签（不得同一人）");
        }
        // 输注血袋必须是本申请实际发出的血袋
        Long issued = issueMapper.selectCount(new LambdaQueryWrapper<BbIssue>()
                .eq(BbIssue::getRequestId, id)
                .eq(BbIssue::getBagId, req.getBagId()));
        if (issued == null || issued == 0) {
            throw new BizException(ErrorCode.A0001, "该血袋未发血至本申请，禁止开始输注");
        }
        BbTransfusion tf = new BbTransfusion();
        tf.setRequestId(id);
        tf.setBagId(req.getBagId());
        tf.setExecutorId(CurrentUser.id());
        tf.setChecker1Id(CurrentUser.id());
        tf.setChecker2Id(req.getChecker2Id());
        tf.setVitalBefore(req.getVitalBefore());
        tf.setStartTime(LocalDateTime.now());
        try {
            transfusionMapper.insert(tf);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.A0001, "输血执行已开始");
        }
        request.setStatus(50);
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "申请状态已变化，请刷新后重试");
        }
        pltService.recordEvent("bb.request.transfused", request.getReqNo(), "{}");
        return tf.getId();
    }

    /** 输血结束（B-11）：50 → 60 闭环 */
    @Transactional
    public void finishTransfusion(Long id, TransfusionFinishRequest req) {
        BbRequest request = requireRequest(id);
        if (request.getStatus() != 50) {
            throw new BizException(ErrorCode.A0001, "输血未开始或已结束");
        }
        BbTransfusion tf = transfusionMapper.selectOne(new LambdaQueryWrapper<BbTransfusion>()
                .eq(BbTransfusion::getRequestId, id).last("LIMIT 1"));
        if (tf != null) {
            tf.setEndTime(LocalDateTime.now());
            tf.setOutcome(req.getOutcome());
            tf.setNote(req.getNote());
            transfusionMapper.updateById(tf);
        }
        request.setStatus(60);
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "申请状态已变化，请刷新后重试");
        }
        pltService.recordEvent("bb.request.completed", request.getReqNo(),
                "{\"outcome\":" + req.getOutcome() + "}");
    }

    /** 不良反应登记（B-12）：输血中/已结束均可补报 */
    @Transactional
    public Long adverse(Long id, AdverseRequest req) {
        BbRequest request = requireRequest(id);
        if (request.getStatus() < 50) {
            throw new BizException(ErrorCode.A0001, "血液未发血，无输注不良反应可报");
        }
        BbAdverse adverse = new BbAdverse();
        adverse.setRequestId(id);
        adverse.setType(req.getType());
        adverse.setSeverity(req.getSeverity());
        adverse.setHandleNote(req.getHandleNote());
        adverse.setReporterId(CurrentUser.id());
        adverse.setReportTime(LocalDateTime.now());
        adverseMapper.insert(adverse);
        pltService.recordEvent("bb.request.adverse", request.getReqNo(),
                "{\"type\":" + req.getType() + ",\"severity\":" + req.getSeverity() + "}");
        return adverse.getId();
    }

    private BbRequest requireRequest(Long id) {
        BbRequest request = requestMapper.selectById(id);
        if (request == null) {
            throw new BizException(ErrorCode.A0001, "用血申请不存在");
        }
        return request;
    }

    private BbBloodBag requireBag(Long id) {
        BbBloodBag bag = bagMapper.selectById(id);
        if (bag == null) {
            throw new BizException(ErrorCode.A0001, "血袋不存在");
        }
        return bag;
    }
}
