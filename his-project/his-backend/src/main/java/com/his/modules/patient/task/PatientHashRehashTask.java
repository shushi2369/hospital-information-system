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
 * 本任务每次启动游标分批重算全量存量（幂等：已为 HMAC 的行重算结果相同即跳过写）。
 * 全部重算完成后可移除 CryptoUtil.idCardHashCandidates 的 SHA-256 候选。
 *
 * 一百二十六轮【1】【2】P1×2：原实现 LIMIT 2000 无排序无游标 + decrypt 无逐行隔离——
 * ①患者超 2000 行时尾部行永不可达（每次启动重扫前 2000 全跳过）；
 * ②一行脏密文（GCM 校验失败抛 IllegalStateException）中断整批且下次重启卡死在同一行。
 * 现改为 id 游标循环全量 + 逐行 try/catch。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatientHashRehashTask {
    private static final int BATCH = 500;
    /** 全量安全阀：防游标异常时死循环（百万级足够，超限告警退出） */
    private static final int MAX_ROWS = 1_000_000;

    private final PatPatientMapper patientMapper;
    private final CryptoUtil cryptoUtil;

    @EventListener(ApplicationReadyEvent.class)
    public void rehashOnStartup() {
        try {
            rehashAll();
        } catch (Exception e) {
            log.error("id_card_hash 存量重算失败（待下次重启补算）", e);
        }
    }

    /** 游标分批全量重算：按 id 升序推进，单行独立提交，中断即下次续算 */
    public void rehashAll() {
        long cursor = 0;
        int migrated = 0;
        int failed = 0;
        int scanned = 0;
        while (scanned < MAX_ROWS) {
            List<PatPatient> rows = patientMapper.selectList(new LambdaQueryWrapper<PatPatient>()
                    .isNotNull(PatPatient::getIdCardNo)
                    .gt(PatPatient::getId, cursor)
                    .orderByAsc(PatPatient::getId)
                    .last("LIMIT " + BATCH));
            if (rows.isEmpty()) {
                break;
            }
            for (PatPatient p : rows) {
                cursor = p.getId();
                scanned++;
                try {
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
                } catch (Exception rowEx) {
                    // 【1】：脏密文（轮换残留/截断）只跳过该行并计数，不中断批次
                    failed++;
                    log.warn("id_card_hash 重算跳过脏密文行 patientId={}: {}", p.getId(), rowEx.getMessage());
                }
            }
            if (rows.size() < BATCH) {
                break;
            }
        }
        if (migrated > 0 || failed > 0) {
            log.info("id_card_hash 存量重算完成: 迁移 {} 行，脏密文跳过 {} 行（扫描 {} 行）",
                    migrated, failed, scanned);
        }
    }
}
