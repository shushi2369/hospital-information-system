package com.his.modules.pharmacy.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.his.modules.pharmacy.entity.InvInventoryBatch;
import com.his.modules.pharmacy.mapper.InvInventoryBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 效期任务（《05》R14）：每日 00:05 将到期批次置为已过期，过期批次不参与 FEFO 拣批与发药（B4005）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PharmacyExpireTask {
    private final InvInventoryBatchMapper batchMapper;

    @Scheduled(cron = "0 5 0 * * ?")
    public void markExpired() {
        int updated = batchMapper.update(null, new LambdaUpdateWrapper<InvInventoryBatch>()
                .eq(InvInventoryBatch::getStatus, 1)
                .le(InvInventoryBatch::getExpiryDate, LocalDate.now())
                .set(InvInventoryBatch::getStatus, 3)
                .set(InvInventoryBatch::getUpdatedAt, LocalDateTime.now()));
        if (updated > 0) {
            log.info("效期任务完成: {} 个批次置为已过期", updated);
        }
    }
}
