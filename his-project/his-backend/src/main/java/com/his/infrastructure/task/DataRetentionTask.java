package com.his.infrastructure.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据保留策略（九十轮数据生命周期审计 P0②：四张审计/事件表零清理、纯累积）：
 *  sys_operation_log 180 天（~1,960 行/天，params_json 存全量脱敏请求体）
 *  sys_login_log      90 天
 *  cdss_hit           90 天（开药必写的放大写入）
 *  plt_event_log     180 天（上报对账后可转冷）
 *
 * 分批删除（每批 5,000 行循环直至清零）：避免单条大 DELETE 长事务锁表；
 * 批间 sleep 让位业务写入。首次上线即追历史（deletion 从"保留期起点"一路删到当前）。
 * 追加表在自增主键上无空洞，删除命中 created_at 索引（V51）。
 */
@Slf4j
@Component
public class DataRetentionTask {

    private static final int BATCH = 5_000;
    private static final long SLEEP_MS = 200L;

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public DataRetentionTask(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 每日 03:40 执行（错开 00:05~00:30 的日切任务与 02:00 备份） */
    @Scheduled(cron = "0 40 3 * * ?")
    public void purgeExpired() {
        purge("sys_operation_log", LocalDate.now().minusDays(180));
        purge("sys_login_log", LocalDate.now().minusDays(90));
        purge("cdss_hit", LocalDate.now().minusDays(90));
        purge("plt_event_log", LocalDate.now().minusDays(180));
    }

    private void purge(String table, LocalDate keepFrom) {
        LocalDateTime cutoff = keepFrom.atStartOfDay();
        long total = 0;
        try {
            while (true) {
                int n = jdbc.update(
                        "DELETE FROM " + table + " WHERE created_at < ? LIMIT ?", cutoff, BATCH);
                total += n;
                if (n < BATCH) {
                    break;
                }
                Thread.sleep(SLEEP_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("保留策略清理被中断 {}: 已删 {} 行", table, total);
            return;
        } catch (Exception e) {
            log.error("保留策略清理失败 {}: 已删 {} 行", table, total, e);
            return;
        }
        if (total > 0) {
            log.info("保留策略清理完成: {} 删除 {} 行（保留 {} 日起）", table, total, keepFrom);
        }
    }
}
