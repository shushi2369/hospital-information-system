package com.his.modules.ris.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.inp.spi.DischargeCheckHook;
import com.his.modules.ris.entity.RisRequest;
import com.his.modules.ris.mapper.RisRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 出院前置检查：存在未完结的检查申请（待预约/已预约/检查中）则阻断。 */
@Component
@RequiredArgsConstructor
public class RisDischargeCheckHook implements DischargeCheckHook {
    private final RisRequestMapper requestMapper;

    @Override
    public String checkBlocker(Long admissionId) {
        List<RisRequest> open = requestMapper.selectList(
                new LambdaQueryWrapper<RisRequest>()
                        .eq(RisRequest::getAdmissionId, admissionId)
                        .in(RisRequest::getStatus, List.of(10, 20, 30)));
        if (!open.isEmpty()) {
            return "存在未完结的检查申请（" + open.size() + " 条），请先作废或完成检查";
        }
        return null;
    }
}
