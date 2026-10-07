package com.his.modules.mrc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.infrastructure.util.LikeEscapeUtil;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.mrc.dto.BorrowRequest;
import com.his.modules.mrc.dto.HomepageCodeRequest;
import com.his.modules.mrc.dto.HomepageQcRequest;
import com.his.modules.mrc.dto.MrcQuery;
import com.his.modules.mrc.dto.MrcRecordVO;
import com.his.modules.mrc.entity.MrcBorrow;
import com.his.modules.mrc.entity.MrcHomepage;
import com.his.modules.mrc.entity.MrcIcd10;
import com.his.modules.mrc.entity.MrcRecord;
import com.his.modules.mrc.mapper.MrcBorrowMapper;
import com.his.modules.mrc.mapper.MrcHomepageMapper;
import com.his.modules.mrc.mapper.MrcIcd10Mapper;
import com.his.modules.mrc.mapper.MrcRecordMapper;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.patient.app.PatientDTO;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 病案服务（M-01~M-08）：惰性建档、首页编码/质控、归档双前置、借阅。
 */
@Service
@RequiredArgsConstructor
public class MrcService {
    private final MrcRecordMapper recordMapper;
    private final MrcHomepageMapper homepageMapper;
    private final MrcBorrowMapper borrowMapper;
    private final MrcIcd10Mapper icd10Mapper;
    private final InpAppService inpAppService;
    private final PatientAppService patientAppService;
    private final com.his.modules.plt.service.PltService pltService;
    private final com.his.modules.rpt.service.RptService rptService;

    /** 病案分页（M-01）：惰性补建出院未结/已结算住院的待归档病案 */
    public PageResult<MrcRecordVO> page(MrcQuery query) {
        ensureCreated();
        Page<MrcRecord> page = recordMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<MrcRecord>()
                        .eq(query.getArchiveStatus() != null, MrcRecord::getArchiveStatus, query.getArchiveStatus())
                        .eq(query.getQcStatus() != null, MrcRecord::getQcStatus, query.getQcStatus())
                        .like(query.getMrcNo() != null && !query.getMrcNo().isBlank(), MrcRecord::getMrcNo, LikeEscapeUtil.escape(query.getMrcNo()))
                        .orderByDesc(MrcRecord::getId));
        Map<Long, PatientDTO> patients = patientAppService.listByIds(
                        page.getRecords().stream().map(MrcRecord::getPatientId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(PatientDTO::getId, p -> p));
        PageResult<MrcRecordVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setList(page.getRecords().stream().map(r -> {
            MrcRecordVO vo = new MrcRecordVO();
            vo.setId(r.getId());
            vo.setMrcNo(r.getMrcNo());
            vo.setAdmissionId(r.getAdmissionId());
            vo.setPatientId(r.getPatientId());
            PatientDTO p = patients.get(r.getPatientId());
            vo.setPatientName(p == null ? null : p.getName());
            vo.setArchiveStatus(r.getArchiveStatus());
            vo.setQcStatus(r.getQcStatus());
            vo.setArchiveTime(r.getArchiveTime());
            return vo;
        }).toList());
        return result;
    }

    /** 惰性创建：出院未结(20)/已结算(30) 的住院若无病案记录则补建（幂等） */
    @Transactional
    public void ensureCreated() {
        List<InpAdmission> admissions = inpAppService.listAdmissionsByStatuses(List.of(20, 30));
        if (admissions.isEmpty()) {
            return;
        }
        // 批量比对（三十三轮性能：原对每个已出院住院逐条 COUNT，随历史线性增长 → 2 次批查）
        List<Long> admissionIds = admissions.stream().map(InpAdmission::getId).toList();
        java.util.Set<Long> existing = recordMapper.selectList(new LambdaQueryWrapper<MrcRecord>()
                        .in(MrcRecord::getAdmissionId, admissionIds)
                        .select(MrcRecord::getAdmissionId))
                .stream().map(MrcRecord::getAdmissionId).collect(java.util.stream.Collectors.toSet());
        for (InpAdmission admission : admissions) {
            if (!existing.contains(admission.getId())) {
                MrcRecord record = new MrcRecord();
                record.setMrcNo(admission.getAdmissionNo());
                record.setAdmissionId(admission.getId());
                record.setPatientId(admission.getPatientId());
                record.setArchiveStatus(10);
                record.setQcStatus(0);
                record.setStatus(1);
                try {
                    recordMapper.insert(record);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    // 并发惰性补建：唯一索引兜底，另一方已建则忽略
                }
            }
        }
    }

    public MrcRecord requireRecord(Long admissionId) {
        ensureCreated();
        MrcRecord record = recordMapper.selectOne(new LambdaQueryWrapper<MrcRecord>()
                .eq(MrcRecord::getAdmissionId, admissionId).last("LIMIT 1"));
        if (record == null) {
            throw new BizException(ErrorCode.A0001, "病案不存在");
        }
        return record;
    }

    /** 首页编码（M-03）：出院未结后即可编码 */
    @Transactional
    public void code(Long admissionId, HomepageCodeRequest req) {
        InpAdmission admission = inpAppService.requireAdmission(admissionId);
        if (admission.getStatus() == 10) {
            throw new BizException(ErrorCode.B6003, "住院在院，不能编码首页");
        }
        // 归档(20)/借阅中(30)冻结：法定病案首页归档后不可篡改且无版本留痕（八十八轮状态机审计）
        MrcRecord recordForFreeze = recordMapper.selectOne(new LambdaQueryWrapper<MrcRecord>()
                .eq(MrcRecord::getAdmissionId, admissionId).last("LIMIT 1"));
        if (recordForFreeze != null && recordForFreeze.getArchiveStatus() != null
                && recordForFreeze.getArchiveStatus() != 10) {
            throw new BizException(ErrorCode.B6303, "病案已归档或借阅中，首页编码冻结");
        }
        MrcHomepage homepage = homepageMapper.selectOne(new LambdaQueryWrapper<MrcHomepage>()
                .eq(MrcHomepage::getAdmissionId, admissionId).last("LIMIT 1"));
        if (homepage == null) {
            homepage = new MrcHomepage();
            homepage.setAdmissionId(admissionId);
        }
        homepage.setMainDiagnosisCode(req.getMainDiagnosisCode());
        homepage.setMainDiagnosisName(req.getMainDiagnosisName());
        try {
            homepage.setOtherDiagnoses(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(req.getOtherDiagnoses() == null ? List.of() : req.getOtherDiagnoses()));
        } catch (Exception ignored) {
            homepage.setOtherDiagnoses("[]");
        }
        homepage.setOperationCode(req.getOperationCode());
        homepage.setCoderId(CurrentUser.id());
        homepage.setCodeTime(LocalDateTime.now());
        if (homepage.getId() == null) {
            homepage.setStatus(1);
            homepageMapper.insert(homepage);
        } else {
            homepageMapper.updateById(homepage);
        }
    }

    /** 首页查询（GET 必须只读，八十八轮审计 S3）：费用汇总每次现算，不再懒物化回写
     *  （原 GET 内 updateById 无锁无事务，并发 GET 互相覆盖，预取/重放即触发写） */
    public MrcHomepage homepage(Long admissionId) {
        MrcHomepage homepage = homepageMapper.selectOne(new LambdaQueryWrapper<MrcHomepage>()
                .eq(MrcHomepage::getAdmissionId, admissionId).last("LIMIT 1"));
        if (homepage != null && homepage.getChargeSummary() == null) {
            homepage.setChargeSummary(toJson(chargeSummary(admissionId)));
        }
        return homepage;
    }

    /** 费用分类汇总（首页费用栏，按一日清/收费明细类别聚合） */
    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> chargeSummary(Long admissionId) {
        Map<String, Object> summary = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        try {
            var fees = inpAppService.listUnpaidDailyFees(admissionId);
            Map<Integer, BigDecimal> byType = new HashMap<>();
            for (var f : fees) {
                byType.merge(f.getFeeType(), f.getAmount(), BigDecimal::add);
                total = total.add(f.getAmount());
            }
            summary.put("byType", byType);
        } catch (Exception ignored) {
            // 已结算住院无未结费用
        }
        summary.put("total", total);
        return summary;
    }

    /** 首页质控（M-04）：通过/退回 */
    @Transactional
    public void qc(Long admissionId, HomepageQcRequest req) {
        MrcRecord record = requireRecord(admissionId);
        MrcHomepage homepage = homepageMapper.selectOne(new LambdaQueryWrapper<MrcHomepage>()
                .eq(MrcHomepage::getAdmissionId, admissionId).last("LIMIT 1"));
        if (homepage == null || homepage.getCodeTime() == null) {
            throw new BizException(ErrorCode.A0001, "首页尚未编码");
        }
        // 归档(20)/借阅中(30)冻结：归档后质控结论不可改写（archive 前置 qcStatus==1 的不变量）
        if (record.getArchiveStatus() == null || record.getArchiveStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "病案已归档或借阅中，质控结论冻结");
        }
        int qcStatus = Boolean.TRUE.equals(req.getPass()) ? 1 : 2;
        int updated = recordMapper.update(null, new LambdaUpdateWrapper<MrcRecord>()
                .eq(MrcRecord::getId, record.getId())
                .set(MrcRecord::getQcStatus, qcStatus)
                .set(MrcRecord::getQcBy, CurrentUser.id())
                .set(MrcRecord::getQcTime, LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0008, "病案状态已变化，请刷新后重试");
        }
    }

    /** 归档（M-02）：双前置——住院已结算 + 首页质控通过 */
    @Transactional
    public void archive(Long admissionId) {
        MrcRecord record = requireRecord(admissionId);
        InpAdmission admission = inpAppService.requireAdmission(admissionId);
        if (admission.getStatus() != 30) {
            throw new BizException(ErrorCode.B6301);
        }
        if (record.getQcStatus() == null || record.getQcStatus() != 1) {
            throw new BizException(ErrorCode.B6302);
        }
        if (record.getArchiveStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "病案当前状态不可归档（已归档或借阅中）");
        }
        record.setArchiveStatus(20);
        record.setArchiveTime(LocalDateTime.now());
        if (recordMapper.updateById(record) != 1) {
            throw new BizException(ErrorCode.A0008, "病案状态已变化，请刷新后重试");
        }
        pltService.recordEvent("mrc.archived", record.getMrcNo(), "{}");
        // 五期-lite：病案归档触发区域上报
        com.his.modules.patient.app.PatientDTO mrcPt = patientAppService.getById(record.getPatientId());
        rptService.enqueue(new com.his.modules.rpt.service.RptService.EnqueueCmd(
                com.his.modules.rpt.entity.RptUpload.TYPE_ARCHIVE, record.getId(), record.getMrcNo(),
                record.getPatientId(), mrcPt == null ? "" : mrcPt.getName(), "MRC_ARCHIVED", record.getArchiveTime(),
                java.util.Map.of("admissionId", record.getAdmissionId())));
    }

    /** 借阅（M-06）：仅已归档病案可借 */
    @Transactional
    public Long borrow(Long admissionId, BorrowRequest req) {
        MrcRecord record = requireRecord(admissionId);
        if (record.getArchiveStatus() == 10) {
            throw new BizException(ErrorCode.B6302, "病案尚未归档，不能借阅");
        }
        if (record.getArchiveStatus() == 30) {
            throw new BizException(ErrorCode.B6303);
        }
        MrcBorrow borrow = new MrcBorrow();
        borrow.setMrcId(record.getId());
        borrow.setBorrowerId(req.getBorrowerId() == null ? CurrentUser.id() : req.getBorrowerId());
        borrow.setBorrowTime(LocalDateTime.now());
        borrow.setExpectReturnTime(req.getExpectReturnDays() == null ? null
                : java.time.LocalDate.now().plusDays(req.getExpectReturnDays()));
        borrow.setStatus(1);
        borrowMapper.insert(borrow);
        // 条件更新断言借出前态：0 行（并发归还/归档）时不能让借阅单悬挂在"已借出"之外
        int updated = recordMapper.update(null, new LambdaUpdateWrapper<MrcRecord>()
                .eq(MrcRecord::getId, record.getId())
                .eq(MrcRecord::getArchiveStatus, 20)
                .set(MrcRecord::getArchiveStatus, 30));
        if (updated != 1) {
            throw new BizException(ErrorCode.B6303);
        }
        return borrow.getId();
    }

    /** 归还（M-07） */
    @Transactional
    public void giveBack(Long admissionId) {
        MrcRecord record = requireRecord(admissionId);
        MrcBorrow borrow = borrowMapper.selectOne(new LambdaQueryWrapper<MrcBorrow>()
                .eq(MrcBorrow::getMrcId, record.getId())
                .eq(MrcBorrow::getStatus, 1)
                .last("LIMIT 1"));
        if (borrow == null) {
            throw new BizException(ErrorCode.B6303, "该病案无借阅中的记录");
        }
        int updated = borrowMapper.update(null, new LambdaUpdateWrapper<MrcBorrow>()
                .eq(MrcBorrow::getId, borrow.getId())
                .eq(MrcBorrow::getStatus, 1)
                .set(MrcBorrow::getStatus, 2)
                .set(MrcBorrow::getReturnTime, LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException(ErrorCode.B6303, "借阅记录已变化，请刷新后重试");
        }
        int back = recordMapper.update(null, new LambdaUpdateWrapper<MrcRecord>()
                .eq(MrcRecord::getId, record.getId())
                .eq(MrcRecord::getArchiveStatus, 30)
                .set(MrcRecord::getArchiveStatus, 20));
        if (back != 1) {
            throw new BizException(ErrorCode.A0008, "病案状态已变化，请刷新后重试");
        }
    }

    /** ICD-10 字典查询 */
    public List<MrcIcd10> icd10(String keyword) {
        return icd10Mapper.selectList(new LambdaQueryWrapper<MrcIcd10>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MrcIcd10::getCode, LikeEscapeUtil.escape(keyword))
                        .or().like(MrcIcd10::getName, LikeEscapeUtil.escape(keyword)))
                .last("LIMIT 50"));
    }
}
