package com.his.modules.bb.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.bb.entity.BbRequest;
import com.his.modules.bb.mapper.BbRequestMapper;
import com.his.modules.inp.spi.DischargeCheckHook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 一百一十五轮 D-04：出院前置检查——用血申请链阻断。
 * 原链路漏挂 bb：患者已发血(40)/输血中(50)可正常出院，出院后 startTransfusion
 * 还能开始输注（输血闭环在院外完成，不良反应登记无从追溯）。
 * 待审核(10) 同样须阻断：放行后可在院外审核、配血、发血、开始输注。
 */
@Component
@RequiredArgsConstructor
public class BbDischargeCheckHook implements DischargeCheckHook {
    private final BbRequestMapper requestMapper;

    @Override
    public String checkBlocker(Long admissionId) {
        List<BbRequest> open = requestMapper.selectList(new LambdaQueryWrapper<BbRequest>()
                .eq(BbRequest::getAdmissionId, admissionId)
                .in(BbRequest::getStatus, List.of(10, 20, 30, 40, 50)));
        if (!open.isEmpty()) {
            return "存在未完结的用血申请（" + open.size() + " 条），请先完成输血闭环或取消申请";
        }
        return null;
    }
}
