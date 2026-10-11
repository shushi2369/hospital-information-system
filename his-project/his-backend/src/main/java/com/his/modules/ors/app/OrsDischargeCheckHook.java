package com.his.modules.ors.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.inp.spi.DischargeCheckHook;
import com.his.modules.ors.entity.OrsSurgeryRequest;
import com.his.modules.ors.mapper.OrsSurgeryRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 出院前置检查：存在未完结的手术申请（待审核/已审核/已排台/术中/复苏中）则阻断。 */
@Component
@RequiredArgsConstructor
public class OrsDischargeCheckHook implements DischargeCheckHook {
    private final OrsSurgeryRequestMapper requestMapper;

    @Override
    public String checkBlocker(Long admissionId) {
        List<OrsSurgeryRequest> open = requestMapper.selectList(
                new LambdaQueryWrapper<OrsSurgeryRequest>()
                        .eq(OrsSurgeryRequest::getAdmissionId, admissionId)
                        // 一百一十四轮 #3：补 60（已离室未关档）——漏掉时患者可出院，此后 complete()
                        // 因 requireInHospital 必抛，手术费/麻醉费永久漏记且无恢复路径
                        .in(OrsSurgeryRequest::getStatus, List.of(10, 20, 30, 40, 50, 60)));
        if (!open.isEmpty()) {
            return "存在未完结的手术申请（" + open.size() + " 条），请先取消或完成手术";
        }
        return null;
    }
}
