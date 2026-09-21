package com.his.modules.plt.service;

import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.plt.entity.PltEventLog;
import com.his.modules.plt.mapper.PltEventLogMapper;
import com.his.modules.plt.mapper.PltIdMapMapper;
import com.his.modules.plt.entity.PltIdMap;
import com.his.modules.plt.entity.PltMasterIndex;
import com.his.modules.plt.mapper.PltMasterIndexMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台底座服务：EMPI 注册/查询/合并 + 集成事件 outbox 落库。
 * 事件在业务事务内写入（同事务提交/回滚），供联调查询与未来 ESB 投递（《08》§3）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PltService {
    private final PltMasterIndexMapper masterIndexMapper;
    private final PltIdMapMapper idMapMapper;
    private final PltEventLogMapper eventLogMapper;
    private final com.his.modules.patient.mapper.PatPatientMapper patPatientMapper;
    private final IdGenerator idGenerator;

    /** 为一期患者注册主索引（建档后调用；幂等：已有则跳过） */
    @Transactional(propagation = Propagation.REQUIRED)
    public String registerMpi(Long patientId) {
        PltMasterIndex existing = masterIndexMapper.selectByPatientId(patientId);
        if (existing != null) {
            return existing.getMpiNo();
        }
        PltMasterIndex mpi = new PltMasterIndex();
        mpi.setMpiNo(idGenerator.next("M"));
        mpi.setPatientId(patientId);
        mpi.setMergeFlag(0);
        masterIndexMapper.insert(mpi);
        PltIdMap map = new PltIdMap();
        map.setMpiId(mpi.getId());
        map.setSourceSystem("HIS-PATIENT");
        map.setSourceId(String.valueOf(patientId));
        idMapMapper.insert(map);
        return mpi.getMpiNo();
    }

    public PltMasterIndex getByPatientId(Long patientId) {
        return masterIndexMapper.selectByPatientId(patientId);
    }

    /** 患者合并（机制先行：标记合并 + 事件留痕；一期无重复档场景） */
    @Transactional
    public String merge(String sourceMpiNo, String targetMpiNo) {
        // 按操作员可见的主索引号定位（原 Long id 无任何查询接口暴露，功能不可达——查验二十三轮）
        PltMasterIndex source = masterIndexMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PltMasterIndex>()
                        .eq(PltMasterIndex::getMpiNo, sourceMpiNo).last("LIMIT 1"));
        PltMasterIndex target = masterIndexMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PltMasterIndex>()
                        .eq(PltMasterIndex::getMpiNo, targetMpiNo).last("LIMIT 1"));
        if (source == null || target == null) {
            throw new BizException(ErrorCode.A0001, "主索引不存在");
        }
        if (source.getMergeFlag() == 1) {
            throw new BizException(ErrorCode.A0001, "该主索引已合并");
        }
        // 目标须为未合并索引：合并到已合并目标会产生断链（A→B→C 归一需链解析，查验二十七轮）
        if (target.getMergeFlag() != null && target.getMergeFlag() == 1) {
            throw new BizException(ErrorCode.A0001, "目标主索引已合并至其他索引，请直接合并至最终索引");
        }
        source.setMergeFlag(1);
        source.setMergedInto(target.getId());
        masterIndexMapper.updateById(source);
        // 联动停用源患者（业界"合并后数据继续分裂"对标）：历史单据保留原 patient_id
        // （经 EMPI 归一链可见），源患者置停用防止合并后继续产生新单据
        if (source.getPatientId() != null) {
            com.his.modules.patient.entity.PatPatient sourcePatient =
                    patPatientMapper.selectById(source.getPatientId());
            if (sourcePatient != null && sourcePatient.getStatus() != null && sourcePatient.getStatus() == 1) {
                sourcePatient.setStatus(0);
                patPatientMapper.updateById(sourcePatient);
            }
        }
        recordEvent("empi.merged", source.getMpiNo(),
                "{\"from\":\"" + source.getMpiNo() + "\",\"to\":\"" + target.getMpiNo() + "\"}");
        return target.getMpiNo();
    }

    /** 事件落库（业务事务内调用，同事务提交/回滚） */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordEvent(String eventType, String bizNo, String payload) {
        try {
            PltEventLog event = new PltEventLog();
            event.setEventNo(idGenerator.next("EV"));
            event.setEventType(eventType);
            event.setBizNo(bizNo);
            event.setPayload(payload);
            eventLogMapper.insert(event);
        } catch (Exception e) {
            // 事件落库失败不阻断业务（审计与操作日志仍可追溯），仅告警
            log.error("集成事件落库失败 type={} biz={}", eventType, bizNo, e);
        }
    }

}
