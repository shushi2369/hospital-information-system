package com.his.modules.alert.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.alert.entity.AlertCritical;
import com.his.modules.alert.mapper.AlertCriticalMapper;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一百一十四轮 #7：危急值滞留超时升级——原闭环全靠人肉催办，滞留无任何提醒。
 * 扫描口径（教学库 30 分钟粒度）：待通知(10) 超 30 分钟、待确认(20) 超 60 分钟、
 * 已确认未闭环(30) 超 4 小时 → plt 平台事件留痕（接入消息推送后可升级为提醒）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertOverdueTask {
    private final AlertCriticalMapper alertMapper;
    private final PltService pltService;

    @Scheduled(cron = "0 */30 * * * ?")
    public void scanOverdue() {
        LocalDateTime now = LocalDateTime.now();
        scan(10, now.minusMinutes(30), "危急值通知超时(>30min)");
        scan(20, now.minusMinutes(60), "危急值确认超时(>60min)");
        scan(30, now.minusHours(4), "危急值闭环超时(>4h)");
    }

    private void scan(int status, LocalDateTime before, String label) {
        try {
            List<AlertCritical> overdue = alertMapper.selectList(new LambdaQueryWrapper<AlertCritical>()
                    .eq(AlertCritical::getStatus, status)
                    .lt(AlertCritical::getCreatedAt, before)
                    .last("LIMIT 50"));
            for (AlertCritical a : overdue) {
                pltService.recordEvent("alert.overdue", a.getAlertNo(),
                        "{\"status\":" + status + ",\"label\":\"" + label + "\"}");
            }
            if (!overdue.isEmpty()) {
                log.warn("危急值超时升级: status={} 滞留 {} 条", status, overdue.size());
            }
        } catch (Exception e) {
            log.error("危急值超时扫描失败 status={}", status, e);
        }
    }
}
