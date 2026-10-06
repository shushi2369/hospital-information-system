package com.his.modules.inp.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.ChargeItemDTO;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.inp.entity.InpBed;
import com.his.modules.inp.entity.InpDailyFee;
import com.his.modules.inp.mapper.InpAdmissionMapper;
import com.his.modules.inp.mapper.InpBedMapper;
import com.his.modules.inp.mapper.InpDailyFeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 床位费日结任务（《09》§5.6）：每日 00:20 为前一日在院患者自动记床位费。
 * 简化口径：仅对任务时刻状态=在院 且 入院时间早于前一日结束的住院计费；出院当日的尾日费用按出院时点手工/结算口径处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionFeeTask {
    private final InpAdmissionMapper admissionMapper;
    private final InpBedMapper bedMapper;
    private final InpDailyFeeMapper dailyFeeMapper;
    private final BasedataAppService basedataAppService;

    @Scheduled(cron = "0 20 0 * * ?")
    public void recordBedFees() {
        recordBedFeesFor(LocalDate.now().minusDays(1));
    }

    /** 启动补偿（九十轮资源审计 P1-3）：00:20 停机窗口错过日结时，启动即补记昨日床位费。
     *  幂等（admission+feeDate+sourceType exists 检查），重复调用安全 */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        recordBedFeesFor(LocalDate.now().minusDays(1));
    }

    public void recordBedFeesFor(LocalDate feeDate) {
        LocalDateTime dayEnd = feeDate.plusDays(1).atStartOfDay(); // 半开区间上界（八十七轮审计 P2-1）
        List<InpAdmission> admissions = admissionMapper.selectList(
                new LambdaQueryWrapper<InpAdmission>().eq(InpAdmission::getStatus, 10));
        int count = 0;
        for (InpAdmission admission : admissions) {
            if (admission.getAdmissionTime().isAfter(dayEnd)) {
                continue;
            }
            Long exists = dailyFeeMapper.selectCount(new LambdaQueryWrapper<InpDailyFee>()
                    .eq(InpDailyFee::getAdmissionId, admission.getId())
                    .eq(InpDailyFee::getFeeDate, feeDate)
                    .eq(InpDailyFee::getSourceType, 1));
            if (exists != null && exists > 0) {
                continue;
            }
            InpBed bed = bedMapper.selectById(admission.getBedId());
            if (bed == null) {
                continue;
            }
            ChargeItemDTO item = basedataAppService.getChargeItem(bed.getChargeItemId());
            if (item == null) {
                continue;
            }
            InpDailyFee fee = new InpDailyFee();
            fee.setAdmissionId(admission.getId());
            fee.setFeeDate(feeDate);
            fee.setFeeType(item.getCategory());
            fee.setSourceType(1);
            fee.setSourceDetailId(bed.getId());
            fee.setItemName(item.getItemName() + "（" + bed.getBedNo() + "）");
            fee.setQuantity(BigDecimal.ONE);
            fee.setUnitPrice(item.getPrice());
            fee.setAmount(item.getPrice().setScale(2, RoundingMode.HALF_UP));
            fee.setChargeStatus(0);
            fee.setStatus(1);
            dailyFeeMapper.insert(fee);
            count++;
        }
        if (count > 0) {
            log.info("床位费日结完成: {} 条 (费用日期 {})", count, feeDate);
        }
    }
}
