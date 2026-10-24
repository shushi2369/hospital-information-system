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
            // 一百二十六轮【3】P2：ORDER BY 定序（原无排序时滞留 >200 条尾部单据可能永不被扫）
            List<AlertCritical> rows = alertMapper.selectList(new LambdaQueryWrapper<AlertCritical>()
                    .eq(AlertCritical::getStatus, status)
                    .orderByAsc(AlertCritical::getId)
                    .last("LIMIT 200"));
            int overdue = 0;
            for (AlertCritical a : rows) {
                // 去重：同一单同一状态只上报一次（实体无升级标记列——按 plt 事件查重，
                // 避免 30 分钟一轮重复灌入事件表；查不到事件表时兜底为首次上报）
                Long reported = pltService.countEvents("alert.overdue", a.getAlertNo());
                if (reported != null && reported > 0) {
                    continue;
                }
                // 各状态按进入该状态的时刻计时：待确认从通知时间起算、已确认从确认时间起算。
                // 用 createdAt 会把"滞留很久后才通知"的单据在下一次扫描立刻误判超时
                LocalDateTime since = status == 20 ? a.getNotifiedAt()
                        : status == 30 ? a.getConfirmedAt() : a.getCreatedAt();
                if (since == null) {
                    since = a.getCreatedAt();
                }
                if (since == null || !since.isBefore(before)) {
                    continue;
                }
                overdue++;
                pltService.recordEvent("alert.overdue", a.getAlertNo(),
                        "{\"status\":" + status + ",\"label\":\"" + label + "\"}");
            }
            if (overdue > 0) {
                log.warn("危急值超时升级: status={} 滞留 {} 条", status, overdue);
            }
        } catch (Exception e) {
            log.error("危急值超时扫描失败 status={}", status, e);
        }
    }
}
