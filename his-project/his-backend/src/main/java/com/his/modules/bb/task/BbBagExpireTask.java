package com.his.modules.bb.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.his.modules.bb.entity.BbBloodBag;
import com.his.modules.bb.mapper.BbBloodBagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 血袋效期巡检（一百五十二轮 D-35，A 代理 150 轮 P3-5）：
 * 在库(status=1)但已过效期的血袋每日自动置报废(3)——原仅配血/发血时按日期硬拦，
 * 台账"在库≠可用"口径虚高（实测 139 袋在库 137 袋已过期），配血员逐袋撞墙。
 * 幂等：status=1 且 expire_date < 今天 才会被更新，重复执行安全。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BbBagExpireTask {

    private final BbBloodBagMapper bagMapper;

    /** 每日 01:30 巡检（错开 00:20 床位费日结与 02:00 备份） */
    @Scheduled(cron = "0 30 1 * * ?")
    public void scrapExpiredBags() {
        scrapExpiredBagsFor(LocalDate.now());
    }

    /** 启动补偿：停机错过巡检时启动即扫一遍 */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        try {
            scrapExpiredBagsFor(LocalDate.now());
        } catch (Exception e) {
            log.error("血袋效期启动巡检失败（待下次 cron 补扫）", e);
        }
    }

    private void scrapExpiredBagsFor(LocalDate today) {
        List<BbBloodBag> expired = bagMapper.selectList(new LambdaQueryWrapper<BbBloodBag>()
                .eq(BbBloodBag::getStatus, 1)
                .lt(BbBloodBag::getExpireDate, today)
                .last("LIMIT 500"));
        if (expired.isEmpty()) {
            return;
        }
        int updated = bagMapper.update(null, new LambdaUpdateWrapper<BbBloodBag>()
                .eq(BbBloodBag::getStatus, 1)
                .lt(BbBloodBag::getExpireDate, today)
                .in(BbBloodBag::getId, expired.stream().map(BbBloodBag::getId).toList())
                .set(BbBloodBag::getStatus, 3));
        log.warn("血袋效期巡检：{} 袋过期在库血袋已置报废（效期<{}）", updated, today);
    }
}
