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
        // 自合并守卫：mergedInto 指向自身会形成断链，且源患者会被误停用（三十五轮）
        if (source.getId().equals(target.getId())) {
            throw new BizException(ErrorCode.A0001, "源与目标为同一主索引，不能自合并");
        }
        // 行锁：按 id 升序锁两行（避免 A→B 与 B→A 并发合并互等死锁），锁后重读再校验，
        // 防止两笔并发合并同时通过校验、后提交者覆盖 mergedInto 造成断链
        Long lowId = Math.min(source.getId(), target.getId());
        Long highId = Math.max(source.getId(), target.getId());
        PltMasterIndex low = masterIndexMapper.selectByIdForUpdate(lowId);
        PltMasterIndex high = masterIndexMapper.selectByIdForUpdate(highId);
        if (low == null || high == null) {
            throw new BizException(ErrorCode.A0001, "主索引不存在");
        }
        source = source.getId().equals(lowId) ? low : high;
        target = target.getId().equals(lowId) ? low : high;
        if (Integer.valueOf(1).equals(source.getMergeFlag())) {
            throw new BizException(ErrorCode.A0001, "该主索引已合并");
        }
        // 目标须为未合并索引：合并到已合并目标会产生断链（A→B→C 归一需链解析，查验二十七轮）
        if (Integer.valueOf(1).equals(target.getMergeFlag())) {
            throw new BizException(ErrorCode.A0001, "目标主索引已合并至其他索引，请直接合并至最终索引");
        }
        int merged = masterIndexMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PltMasterIndex>()
                        .eq(PltMasterIndex::getId, source.getId())
                        .eq(PltMasterIndex::getMergeFlag, 0)
                        .set(PltMasterIndex::getMergeFlag, 1)
                        .set(PltMasterIndex::getMergedInto, target.getId()));
        if (merged != 1) {
            throw new BizException(ErrorCode.A0008, "主索引状态已变化，请刷新后重试");
        }
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

    /** 按类型+单号查事件数（一百二十六轮【3】：AlertOverdueTask 去重判定用） */
    public Long countEvents(String eventType, String bizNo) {
        return eventLogMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PltEventLog>()
                .eq(PltEventLog::getEventType, eventType)
                .eq(PltEventLog::getBizNo, bizNo));
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
