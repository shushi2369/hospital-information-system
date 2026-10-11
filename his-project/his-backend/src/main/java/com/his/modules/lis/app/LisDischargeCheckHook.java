package com.his.modules.lis.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.inp.spi.DischargeCheckHook;
import com.his.modules.lis.entity.LisRequest;
import com.his.modules.lis.mapper.LisRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 一百一十五轮 D-04：出院前置检查——检验申请链提示。
 * 原链路漏挂 lis：待采集/已采集/检验中的标本在患者出院后流程悬空（标本离院）。
 * 检验周期短且可院外完成采样收尾，故仅提示不硬阻断（与 bb 输血中硬阻断区分）。
 */
@Component
@RequiredArgsConstructor
public class LisDischargeCheckHook implements DischargeCheckHook {
    private final LisRequestMapper requestMapper;

    @Override
    public String checkBlocker(Long admissionId) {
        List<LisRequest> open = requestMapper.selectList(new LambdaQueryWrapper<LisRequest>()
                .eq(LisRequest::getAdmissionId, admissionId)
                .in(LisRequest::getStatus, List.of(10, 20, 30)));
        if (!open.isEmpty()) {
            return "存在未完结的检验申请（" + open.size() + " 条），请确认标本采集与报告回收";
        }
        return null;
    }
}
