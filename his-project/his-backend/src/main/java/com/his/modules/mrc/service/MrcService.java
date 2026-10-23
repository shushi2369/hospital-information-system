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
    private final com.his.modules.system.app.SystemAppService systemAppService;
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
                createRecordFor(admission);
            }
        }
    }

    private void createRecordFor(InpAdmission admission) {
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

    public MrcRecord requireRecord(Long admissionId) {
        ensureCreated();
        MrcRecord record = recordMapper.selectOne(new LambdaQueryWrapper<MrcRecord>()
                .eq(MrcRecord::getAdmissionId, admissionId).last("LIMIT 1"));
        if (record == null) {
            // 批量补建只看最近 500 条出院记录：存量更大时老病案会永久漏建，按需为当前住院补建
            InpAdmission admission = inpAppService.getAdmission(admissionId);
            Integer status = admission == null ? null : admission.getStatus();
            if (status != null && (status == 20 || status == 30)) {
                createRecordFor(admission);
                record = recordMapper.selectOne(new LambdaQueryWrapper<MrcRecord>()
                        .eq(MrcRecord::getAdmissionId, admissionId).last("LIMIT 1"));
            }
        }
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
        // 锁读冻结标记：否则校验通过后并发归档可把首页改成"归档后内容"（本方法不改记录版本，乐观锁兜不住）
        MrcRecord recordForFreeze = recordMapper.selectOne(new LambdaQueryWrapper<MrcRecord>()
                .eq(MrcRecord::getAdmissionId, admissionId).last("LIMIT 1 FOR UPDATE"));
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
        String otherJson;
        try {
            otherJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(req.getOtherDiagnoses() == null ? List.of() : req.getOtherDiagnoses());
        } catch (Exception ignored) {
            otherJson = "[]";
        }
        // 编码内容是否变化须在覆盖前判断：质控通过后重新编码（哪怕只改诊断/编码）必须重新质控
        boolean codingChanged = homepage.getId() == null
                || !java.util.Objects.equals(homepage.getMainDiagnosisCode(), req.getMainDiagnosisCode())
                || !java.util.Objects.equals(homepage.getMainDiagnosisName(), req.getMainDiagnosisName())
                || !java.util.Objects.equals(homepage.getOperationCode(), req.getOperationCode())
                || !java.util.Objects.equals(homepage.getOtherDiagnoses(), otherJson);
        homepage.setMainDiagnosisCode(req.getMainDiagnosisCode());
        homepage.setMainDiagnosisName(req.getMainDiagnosisName());
        homepage.setOtherDiagnoses(otherJson);
        homepage.setOperationCode(req.getOperationCode());
        homepage.setCoderId(CurrentUser.id());
        homepage.setCodeTime(LocalDateTime.now());
        if (homepage.getId() == null) {
            homepage.setStatus(1);
            homepageMapper.insert(homepage);
        } else {
            homepageMapper.updateById(homepage);
        }
        // 重新编码即视为质控结论失效：否则质控通过后改编码仍可凭旧结论直接归档
        if (codingChanged && recordForFreeze != null && recordForFreeze.getQcStatus() != null
                && recordForFreeze.getQcStatus() == 1) {
            MrcRecord reset = new MrcRecord();
            reset.setQcStatus(0);
            reset.setVersion(recordForFreeze.getVersion());
            int resetRows = recordMapper.update(reset, new LambdaUpdateWrapper<MrcRecord>()
                    .eq(MrcRecord::getId, recordForFreeze.getId())
                    .eq(MrcRecord::getArchiveStatus, 10));
            if (resetRows != 1) {
                throw new BizException(ErrorCode.A0008, "病案状态已变化，请刷新后重试");
            }
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
        Map<Integer, BigDecimal> byType = new HashMap<>();
        try {
            var fees = inpAppService.listUnpaidDailyFees(admissionId);
            for (var f : fees) {
                byType.merge(f.getFeeType(), f.getAmount(), BigDecimal::add);
                total = total.add(f.getAmount());
            }
        } catch (Exception ignored) {
            // 已结算住院无未结费用
        }
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            // 一百一十轮 M6：未结费用为空不代表没花费——已结算/已归档住院回补全量费用（应收口径）
            try {
                for (var f : inpAppService.listAllDailyFees(admissionId)) {
                    byType.merge(f.getFeeType(), f.getAmount(), BigDecimal::add);
                    total = total.add(f.getAmount());
                }
            } catch (Exception ignored) {
                // 无任何费用记录
            }
        }
        summary.put("byType", byType);
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
        // 版本递增 + 归档态条件：与 archive 的 updateById 乐观锁串行化，
        // 否则并发归档后本更新仍能命中（wrapper 不带版本）把冻结的质控结论改掉
        MrcRecord upd = new MrcRecord();
        upd.setQcStatus(qcStatus);
        upd.setQcBy(CurrentUser.id());
        upd.setQcTime(LocalDateTime.now());
        upd.setVersion(record.getVersion());
        int updated = recordMapper.update(upd, new LambdaUpdateWrapper<MrcRecord>()
                .eq(MrcRecord::getId, record.getId())
                .eq(MrcRecord::getArchiveStatus, 10));
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
        // 条件更新带版本 + qcStatus：并发质控退回（版本递增）后不得再归档
        MrcRecord upd = new MrcRecord();
        upd.setArchiveStatus(20);
        upd.setArchiveTime(LocalDateTime.now());
        upd.setVersion(record.getVersion());
        if (recordMapper.update(upd, new LambdaUpdateWrapper<MrcRecord>()
                .eq(MrcRecord::getId, record.getId())
                .eq(MrcRecord::getArchiveStatus, 10)
                .eq(MrcRecord::getQcStatus, 1)) != 1) {
            throw new BizException(ErrorCode.A0008, "病案状态已变化，请刷新后重试");
        }
        record.setArchiveStatus(20);
        record.setArchiveTime(LocalDateTime.now());
        pltService.recordEvent("mrc.archived", record.getMrcNo(), "{}");
        // 五期-lite：病案归档触发区域上报
        com.his.modules.patient.app.PatientDTO mrcPt = patientAppService.getById(record.getPatientId());
        rptService.enqueue(new com.his.modules.rpt.service.RptService.EnqueueCmd(
                com.his.modules.rpt.entity.RptUpload.TYPE_ARCHIVE, record.getId(), record.getMrcNo(),
                record.getPatientId(), mrcPt == null ? "" : mrcPt.getName(), "MRC_ARCHIVED", record.getArchiveTime(),
                java.util.Map.of("admissionId", record.getAdmissionId())));
    }

    /** 借阅台账（一百一十轮 M4）：分页 + 病案/借阅人/患者信息批量回填（只读） */
    public PageResult<MrcBorrow> borrowPage(com.his.common.PageQuery query, Integer status) {
        Page<MrcBorrow> page = borrowMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<MrcBorrow>()
                        .eq(status != null, MrcBorrow::getStatus, status)
                        .orderByDesc(MrcBorrow::getId));
        var rows = page.getRecords();
        if (!rows.isEmpty()) {
            Map<Long, MrcRecord> records = recordMapper.selectList(new LambdaQueryWrapper<MrcRecord>()
                            .in(MrcRecord::getId, rows.stream().map(MrcBorrow::getMrcId).toList()))
                    .stream().collect(java.util.stream.Collectors.toMap(MrcRecord::getId, r -> r));
            Map<Long, String> users = systemAppService.getUsernameMap(
                    rows.stream().map(MrcBorrow::getBorrowerId).filter(java.util.Objects::nonNull).toList());
            for (MrcBorrow b : rows) {
                MrcRecord r = records.get(b.getMrcId());
                if (r != null) {
                    b.setMrcNo(r.getMrcNo());
                    b.setAdmissionId(r.getAdmissionId());
                    b.setPatientId(r.getPatientId());
                }
                b.setBorrowerName(users.get(b.getBorrowerId()));
            }
            com.his.infrastructure.util.PatientNameBackfill.fill(
                    rows.stream().filter(b -> b.getPatientId() != null).toList(), patientAppService);
        }
        return PageResult.of(page);
    }

    /** 借阅（M-07）：仅已归档病案可借。一百一十一轮子代理 A2：@Transactional 曾错挂在
     *  borrowPage 上导致本写方法裸奔——insert 借阅单自动提交后，条件更新失败抛异常
     *  会残留 status=1 的孤儿借阅单且无法自清 */
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
