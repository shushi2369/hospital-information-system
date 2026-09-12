package com.his.modules.registration.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.his.modules.registration.entity.RegRegistration;
import com.his.modules.registration.mapper.RegRegistrationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 过号任务（《05》R6）：每日 23:55 将当日仍为"已挂号"且未接诊的挂号单置为已过号。
 * 过号不自动退费，可由收费员人工退费。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationExpireTask {
    private final RegRegistrationMapper registrationMapper;

    @Scheduled(cron = "0 55 23 * * ?")
    public void expireToday() {
        int updated = registrationMapper.update(null, new LambdaUpdateWrapper<RegRegistration>()
                .eq(RegRegistration::getRegDate, LocalDate.now())
                .eq(RegRegistration::getStatus, 10)
                .set(RegRegistration::getStatus, 40)
                .set(RegRegistration::getUpdatedAt, LocalDateTime.now()));
        if (updated > 0) {
            log.info("过号任务完成: 当日 {} 条挂号单置为已过号", updated);
        }
    }
}
