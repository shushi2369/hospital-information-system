package com.his.modules.clinic.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.clinic.entity.CliExamApplication;
import com.his.modules.clinic.mapper.CliExamApplicationMapper;
import com.his.modules.inp.spi.DischargeCheckHook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 出院前置检查：存在未收费的住院检查/检验申请则阻断。 */
@Component
@RequiredArgsConstructor
public class ExamFeeCheckHook implements DischargeCheckHook {
    private final CliExamApplicationMapper examApplicationMapper;

    @Override
    public String checkBlocker(Long admissionId) {
        List<CliExamApplication> unpaid = examApplicationMapper.selectList(
                new LambdaQueryWrapper<CliExamApplication>()
                        .eq(CliExamApplication::getAdmissionId, admissionId)
                        .eq(CliExamApplication::getStatus, 10)
                        .eq(CliExamApplication::getChargeStatus, 0));
        if (!unpaid.isEmpty()) {
            return "存在未收费的检查/检验申请（" + unpaid.size() + " 条），请先记账或收费";
        }
        return null;
    }
}
