package com.his.modules.patient.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.BizException;
import com.his.infrastructure.util.LikeEscapeUtil;
import com.his.common.ErrorCode;
import com.his.modules.patient.entity.PatMedicalCard;
import com.his.modules.patient.entity.PatPatient;
import com.his.modules.patient.mapper.PatMedicalCardMapper;
import com.his.modules.patient.mapper.PatPatientMapper;
import com.his.modules.plt.entity.PltMasterIndex;
import com.his.modules.plt.mapper.PltMasterIndexMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * 患者中心跨模块应用服务（挂号/医生工作站/收费模块统一经此读取 pat_ 表）。
 */
@Service
@RequiredArgsConstructor
public class PatientAppService {
    private final PatPatientMapper patientMapper;
    private final PatMedicalCardMapper cardMapper;
    private final PltMasterIndexMapper pltMasterIndexMapper;
    private final com.his.infrastructure.util.CryptoUtil cryptoUtil;

    /** 六十三轮：EMPI 归一读取——源患者已合并时沿链解析到存活主索引的患者，防合并后病历视图分裂 */
    public PatientDTO getById(Long patientId) {
        PatPatient patient = patientMapper.selectById(resolveActivePatientId(patientId));
        if (patient == null) {
            return null;
        }
        PatientDTO dto = toDTO(patient);
        // 主索引详情需要展示身份证（脱敏出站）；列表出口不算解密，保持零开销
        try {
            String plain = cryptoUtil.decrypt(patient.getIdCardNo());
            dto.setMaskedIdCardNo(plain == null ? null
                    : com.his.common.util.MaskUtil.maskIdCard(plain));
        } catch (Exception e) {
            dto.setMaskedIdCardNo(null);
        }
        return dto;
    }

    /**
     * 解析合并链：patient_id → plt_master_index(merge_flag=1) → merged_into(目标主索引 id)
     * → 目标 patient_id，最多 5 跳（A→B→C 归一场景；环路由 merge 写入口守卫阻断）。
     */
    private Long resolveActivePatientId(Long patientId) {
        Long current = patientId;
        for (int hop = 0; hop < 5; hop++) {
            PltMasterIndex idx = pltMasterIndexMapper.selectOne(
                    new LambdaQueryWrapper<PltMasterIndex>()
                            .eq(PltMasterIndex::getPatientId, current)
                            .last("LIMIT 1"));
            if (idx == null || idx.getMergeFlag() == null || idx.getMergeFlag() != 1
                    || idx.getMergedInto() == null) {
                return current;
            }
            PltMasterIndex target = pltMasterIndexMapper.selectById(idx.getMergedInto());
            if (target == null || target.getPatientId() == null) {
                return current;
            }
            current = target.getPatientId();
        }
        return current;
    }

    /** 患者不存在或停用抛 A0001 */
    public PatientDTO requireActive(Long patientId) {
        PatPatient patient = patientMapper.selectById(patientId);
        if (patient == null || patient.getStatus() == 0) {
            throw new BizException(ErrorCode.A0001, "患者不存在或已停用");
        }
        return toDTO(patient);
    }

    /** 患者行锁（住院登记用）：先锁后预检，杜绝同患者并发双入院（对齐退费行锁模式） */
    public void lockForAdmission(Long patientId) {
        patientMapper.lockByIdForUpdate(patientId);
    }

    public List<PatientDTO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return patientMapper.selectBatchIds(ids).stream().map(this::toDTO).toList();
    }

    /**
     * 链归一批量取档（一百六十轮 D-40）：键=原始请求 id，值=归一后目标档案——
     * 与 getById 同口径。原 listByIds 直读源档，EMPI 合并后六大工作台（emc/pe/pub/
     * ris/lis/cnt）与危急值/分诊回填仍显示源档名，与单条详情口径分裂。
     * 按 BFS 层批查 plt_master_index，最多 5 跳（对齐 resolveActivePatientId）。
     */
    public Map<Long, PatientDTO> listByIdsChainAware(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        // 每源独立推进：pos=源id→当前链位；逐跳把处于"已合并"位的源推进到目标档案，
        // 无推进即收敛（≤5 跳对齐 resolveActivePatientId，环路由写入口守卫阻断）
        Map<Long, Long> pos = new java.util.HashMap<>();
        for (Long id : ids) {
            pos.put(id, id);
        }
        for (int hop = 0; hop < 5; hop++) {
            Set<Long> queryIds = new java.util.HashSet<>(pos.values());
            List<PltMasterIndex> idxs = pltMasterIndexMapper.selectList(
                    new LambdaQueryWrapper<PltMasterIndex>().in(PltMasterIndex::getPatientId, queryIds));
            Map<Long, PltMasterIndex> byPatient = new java.util.HashMap<>();
            Set<Long> needTargets = new java.util.HashSet<>();
            for (PltMasterIndex idx : idxs) {
                byPatient.put(idx.getPatientId(), idx);
                if (idx.getMergeFlag() != null && idx.getMergeFlag() == 1 && idx.getMergedInto() != null) {
                    needTargets.add(idx.getMergedInto());
                }
            }
            if (needTargets.isEmpty()) {
                break;
            }
            Map<Long, PltMasterIndex> targetById = pltMasterIndexMapper.selectBatchIds(needTargets).stream()
                    .collect(java.util.stream.Collectors.toMap(PltMasterIndex::getId, x -> x));
            boolean advanced = false;
            for (Map.Entry<Long, Long> e : pos.entrySet()) {
                PltMasterIndex idx = byPatient.get(e.getValue());
                if (idx == null || idx.getMergeFlag() == null || idx.getMergeFlag() != 1
                        || idx.getMergedInto() == null) {
                    continue;
                }
                PltMasterIndex t = targetById.get(idx.getMergedInto());
                if (t != null && t.getPatientId() != null) {
                    e.setValue(t.getPatientId());
                    advanced = true;
                }
            }
            if (!advanced) {
                break;
            }
        }
        Set<Long> activeIds = new java.util.HashSet<>(pos.values());
        Map<Long, PatientDTO> byActive = new java.util.HashMap<>();
        for (PatientDTO p : listByIds(new ArrayList<>(activeIds))) {
            byActive.put(p.getId(), p);
        }
        Map<Long, PatientDTO> out = new java.util.HashMap<>();
        pos.forEach((src, act) -> out.put(src, byActive.get(act)));
        return out;
    }

    /** 按姓名模糊查询患者 ID 集合（EMPI 检索用） */
    public List<Long> searchIdsByName(String name) {
        return patientMapper.selectList(new LambdaQueryWrapper<PatPatient>()
                        .likeRight(PatPatient::getName, LikeEscapeUtil.escape(name)))
                .stream().map(PatPatient::getId).toList();
    }

    /** 按建档号精确查询患者 ID 集合 */
    public List<Long> searchIdsByPatientNo(String patientNo) {
        return patientMapper.selectList(new LambdaQueryWrapper<PatPatient>()
                        .eq(PatPatient::getPatientNo, patientNo))
                .stream().map(PatPatient::getId).toList();
    }

    /** 患者最新有效就诊卡号（无卡返回 null） */
    public String latestCardNo(Long patientId) {
        PatMedicalCard card = cardMapper.selectOne(new LambdaQueryWrapper<PatMedicalCard>()
                .eq(PatMedicalCard::getPatientId, patientId)
                .eq(PatMedicalCard::getStatus, 1)
                .orderByDesc(PatMedicalCard::getId)
                .last("LIMIT 1"));
        return card == null ? null : card.getCardNo();
    }

    private PatientDTO toDTO(PatPatient patient) {
        PatientDTO dto = new PatientDTO();
        dto.setId(patient.getId());
        dto.setPatientNo(patient.getPatientNo());
        dto.setName(patient.getName());
        dto.setGender(patient.getGender());
        dto.setBirthDate(patient.getBirthDate());
        dto.setPhone(patient.getPhone());
        dto.setAllergyHistory(patient.getAllergyHistory());
        dto.setStatus(patient.getStatus());
        return dto;
    }
}
