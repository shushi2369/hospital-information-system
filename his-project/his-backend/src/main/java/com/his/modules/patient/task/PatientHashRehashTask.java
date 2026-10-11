package com.his.modules.patient.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.infrastructure.util.CryptoUtil;
import com.his.modules.patient.entity.PatPatient;
import com.his.modules.patient.mapper.PatPatientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 一百一十四轮 S-02：id_card_hash 存量重算补偿（无盐 SHA-256 → HMAC-SHA256）。
 * 兼容策略：新写入一律 HMAC；查询双候选（HMAC+SHA-256）兜底存量；
 * 本任务每次启动逐步重算存量（幂等：已为 HMAC 的行重算结果相同即跳过写）。
 * 全部重算完成后可移除 CryptoUtil.idCardHashCandidates 的 SHA-256 候选。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatientHashRehashTask {
    private final PatPatientMapper patientMapper;
    private final CryptoUtil cryptoUtil;

    @EventListener(ApplicationReadyEvent.class)
    public void rehashOnStartup() {
        try {
            rehashBatch();
        } catch (Exception e) {
            log.error("id_card_hash 存量重算失败（待下次重启补算）", e);
        }
    }

    /** 同类自调用事务不生效（AOP 限制）——每行 updateById 独立提交，幂等重算中断即下次续算，可接受 */
    public void rehashBatch() {
        // 有界批量：单次最多 2000 行，避免启动期长事务（大库可分页循环，教学库一遍即可）
        List<PatPatient> rows = patientMapper.selectList(new LambdaQueryWrapper<PatPatient>()
                .isNotNull(PatPatient::getIdCardNo)
                .last("LIMIT 2000"));
        int migrated = 0;
        for (PatPatient p : rows) {
            String plain = cryptoUtil.decrypt(p.getIdCardNo());
            if (plain == null || plain.isBlank()) {
                continue;
            }
            String target = cryptoUtil.hmacSha256Hex(plain);
            if (!target.equals(p.getIdCardHash())) {
                p.setIdCardHash(target);
                patientMapper.updateById(p);
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("id_card_hash 存量重算完成: {} 行（HMAC）", migrated);
        }
    }
}
