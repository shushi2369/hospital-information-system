package com.his.modules.pub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.plt.service.PltService;
import com.his.modules.pub.dto.ReceiptRequest;
import com.his.modules.pub.entity.PubDiseaseDict;
import com.his.modules.pub.entity.PubHaiCase;
import com.his.modules.pub.entity.PubInfectiousCard;
import com.his.modules.pub.mapper.PubDiseaseDictMapper;
import com.his.modules.pub.mapper.PubHaiCaseMapper;
import com.his.modules.pub.mapper.PubInfectiousCardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公共卫生服务（四期三批）：传染病报告卡（诊断自动触发→公卫上报→回执闭环）+ 院感病例。
 * 疾控直报为 Mock 口径（回执登记），真实直报 SDK 为外部项。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PubService {
    private final PubInfectiousCardMapper cardMapper;
    private final PubDiseaseDictMapper diseaseDictMapper;
    private final PubHaiCaseMapper haiMapper;
    private final PltService pltService;
    private final IdGenerator idGenerator;
    private final com.his.modules.rpt.service.RptService rptService;
    private final com.his.modules.patient.app.PatientAppService patientAppService;

    /** 传染病报告卡分页（PUB-01） */
    public PageResult<PubInfectiousCard> cardPage(com.his.common.PageQuery query, Integer status) {
        Page<PubInfectiousCard> page = cardMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<PubInfectiousCard>()
                        .eq(status != null, PubInfectiousCard::getStatus, status)
                        .orderByAsc(PubInfectiousCard::getStatus).orderByDesc(PubInfectiousCard::getId));
        return PageResult.of(page);
    }

    /**
     * 诊断自动触发（法定上报）：诊断名包含传染病字典病名 → 自动生成待报卡。
     * 由诊断保存服务调用（clinic → pub 单向）；异常内部消化，不影响诊断保存。
     */
    public void autoCreateCards(Long visitId, Long admissionId, Long patientId,
                                Long doctorId, String diagnosisName) {
        try {
            if (diagnosisName == null || diagnosisName.isBlank()) {
                return;
            }
            List<PubDiseaseDict> dict = diseaseDictMapper.selectList(
                    new LambdaQueryWrapper<PubDiseaseDict>().eq(PubDiseaseDict::getStatus, 1));
            for (PubDiseaseDict d : dict) {
                if (diagnosisName.contains(d.getDiseaseName())) {
                    PubInfectiousCard card = new PubInfectiousCard();
                    card.setCardNo(idGenerator.next("CR"));
                    card.setVisitId(visitId);
                    card.setAdmissionId(admissionId);
                    card.setPatientId(patientId);
                    card.setDoctorId(doctorId);
                    card.setDiseaseName(d.getDiseaseName());
                    card.setDiseaseCategory(d.getCategory());
                    card.setDiagnoseDate(java.time.LocalDate.now());
                    card.setStatus(10);
                    cardMapper.insert(card);
                    pltService.recordEvent("pub.card.autoCreated", card.getCardNo(),
                            "{\"disease\":\"" + com.his.infrastructure.util.JsonEscapeUtil.escape(d.getDiseaseName()) + "\"}");
                }
            }
        } catch (Exception e) {
            log.warn("传染病自动报卡失败 diagnosis {}: {}", diagnosisName, e.getMessage());
        }
    }

    /** 上报登记（PUB-02）：10 待报 → 20 已上报（公卫科） */
    @Transactional
    public void report(Long id) {
        PubInfectiousCard card = requireCard(id);
        if (card.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "报告卡不在待报状态");
        }
        card.setStatus(20);
        card.setReportTime(LocalDateTime.now());
        if (cardMapper.updateById(card) != 1) {
            throw new BizException(ErrorCode.A0008, "报告卡状态已变化，请刷新后重试");
        }
        pltService.recordEvent("pub.card.reported", card.getCardNo(), "{}");
    }

    /** 审核通过（PUB-02）：20 → 30 */
    @Transactional
    public void approve(Long id) {
        PubInfectiousCard card = requireCard(id);
        if (card.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "报告卡未上报，不能审核");
        }
        card.setStatus(30);
        card.setPublicDoctorId(CurrentUser.id());
        if (cardMapper.updateById(card) != 1) {
            throw new BizException(ErrorCode.A0008, "报告卡状态已变化，请刷新后重试");
        }
    }

    /** 回执登记（PUB-03）：30 → 40 疾控回执闭环 */
    @Transactional
    public void receipt(Long id, ReceiptRequest req) {
        PubInfectiousCard card = requireCard(id);
        if (card.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "报告卡未审核，不能登记回执");
        }
        card.setStatus(40);
        card.setReceiptNo(req.getReceiptNo());
        card.setReceiptTime(LocalDateTime.now());
        if (cardMapper.updateById(card) != 1) {
            throw new BizException(ErrorCode.A0008, "报告卡状态已变化，请刷新后重试");
        }
        pltService.recordEvent("pub.card.receipted", card.getCardNo(),
                "{\"receiptNo\":\"" + com.his.infrastructure.util.JsonEscapeUtil.escape(req.getReceiptNo()) + "\"}");
        // 五期-lite：报告卡闭环（40）触发区域平台上报
        com.his.modules.patient.app.PatientDTO rptPt = patientAppService.getById(card.getPatientId());
        rptService.enqueue(new com.his.modules.rpt.service.RptService.EnqueueCmd(
                com.his.modules.rpt.entity.RptUpload.TYPE_INFECTIOUS, card.getId(), card.getCardNo(),
                card.getPatientId(), rptPt == null ? "" : rptPt.getName(), "INF_CARD_CLOSED", card.getReceiptTime(),
                java.util.Map.of("receiptNo", req.getReceiptNo() == null ? "" : req.getReceiptNo())));
    }

    /** 院感病例上报（PUB-H01）：返回病例号 */
    @Transactional
    public String haiReport(PubHaiCase req) {
        // 患者存在性校验（八十八轮 IDOR 审计 P2-2）：防对虚构 patientId 产生公卫记录
        patientAppService.requireActive(req.getPatientId());
        req.setCaseNo(idGenerator.next("GR"));
        req.setStatus(10);
        req.setReporterId(CurrentUser.id());
        req.setDiagnoseDate(java.time.LocalDate.now());
        haiMapper.insert(req);
        pltService.recordEvent("pub.hai.reported", req.getCaseNo(),
                "{\"type\":" + req.getInfectionType() + "}");
        return req.getCaseNo();
    }

    /** 院感确认/整改（PUB-H02）：10 → 20 → 30 */
    @Transactional
    public void haiConfirm(Long id, String note, Integer targetStatus) {
        PubHaiCase c = haiMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.A0001, "院感病例不存在");
        }
        if (targetStatus != 20 && targetStatus != 30) {
            throw new BizException(ErrorCode.A0001, "目标状态非法");
        }
        if (c.getStatus() >= targetStatus) {
            throw new BizException(ErrorCode.A0001, "病例状态已推进");
        }
        c.setStatus(targetStatus);
        c.setConfirmNote(note);
        c.setConfirmTime(LocalDateTime.now());
        if (haiMapper.updateById(c) != 1) {
            throw new BizException(ErrorCode.A0008, "病例状态已变化，请刷新后重试");
        }
        pltService.recordEvent("pub.hai.confirmed", c.getCaseNo(),
                "{\"status\":" + targetStatus + "}");
    }

    private PubInfectiousCard requireCard(Long id) {
        PubInfectiousCard card = cardMapper.selectById(id);
        if (card == null) {
            throw new BizException(ErrorCode.A0001, "报告卡不存在");
        }
        return card;
    }
}
