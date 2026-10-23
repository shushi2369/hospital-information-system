package com.his.modules.doc.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.doc.entity.DocOrder;
import com.his.modules.doc.mapper.DocOrderMapper;
import com.his.modules.inp.spi.DischargeCheckHook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 出院前置检查：存在未停止的长期医嘱则阻断（《05》住院段规则）。 */
@Component
@RequiredArgsConstructor
public class DocOrderStopCheckHook implements DischargeCheckHook {
    private final DocOrderMapper orderMapper;

    @Override
    public String checkBlocker(Long admissionId) {
        List<DocOrder> active = orderMapper.selectList(new LambdaQueryWrapper<DocOrder>()
                .eq(DocOrder::getAdmissionId, admissionId)
                .eq(DocOrder::getOrderClass, 1)
                // 含待审核(10)：出院后药师再审核通过，dispense 会被在院校验永久封死
                .in(DocOrder::getStatus, 10, 20, 30));
        if (!active.isEmpty()) {
            return "存在未停止的长期医嘱（含待审核，" + active.size() + " 条），请先停嘱或作废";
        }
        // 一百一十五轮 D-04：已审未摆药的临时药品医嘱——出院后 dispense 被在院校验永久封死，
        // 挂单无作废联动；出院前须摆药完成或作废
        List<DocOrder> undispensed = orderMapper.selectList(new LambdaQueryWrapper<DocOrder>()
                .eq(DocOrder::getAdmissionId, admissionId)
                .eq(DocOrder::getOrderClass, 2)
                .eq(DocOrder::getCategory, 1)
                .in(DocOrder::getStatus, 10, 20));
        if (!undispensed.isEmpty()) {
            return "存在未摆药的临时药品医嘱（" + undispensed.size() + " 条），请先摆药或作废";
        }
        // 一百一十六轮 F-62：已摆药未执行的临时药同样不得出院——执行接口在院校验，
        // 出院后 execute 永久封死；且摆药后 voidOrder 拒绝作废，医嘱会永远停在 30
        List<DocOrder> undispensedExec = orderMapper.selectList(new LambdaQueryWrapper<DocOrder>()
                .eq(DocOrder::getAdmissionId, admissionId)
                .eq(DocOrder::getOrderClass, 2)
                .eq(DocOrder::getCategory, 1)
                .eq(DocOrder::getStatus, 30));
        if (!undispensedExec.isEmpty()) {
            return "存在已摆药未执行的临时药品医嘱（" + undispensedExec.size()
                    + " 条），请执行完毕后再出院";
        }
        // 临时非药品医嘱：出院后执行单仍在院校验处被封死，未执行会永久悬挂且不计费
        List<DocOrder> pendingNonDrug = orderMapper.selectList(new LambdaQueryWrapper<DocOrder>()
                .eq(DocOrder::getAdmissionId, admissionId)
                .eq(DocOrder::getOrderClass, 2)
                .ne(DocOrder::getCategory, 1)
                .in(DocOrder::getStatus, 20, 30));
        if (!pendingNonDrug.isEmpty()) {
            return "存在未完成的临时医嘱（" + pendingNonDrug.size() + " 条），请执行完毕或作废";
        }
        return null;
    }
}
