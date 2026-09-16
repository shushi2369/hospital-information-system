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
                .in(DocOrder::getStatus, 20, 30));
        if (!active.isEmpty()) {
            return "存在未停止的长期医嘱（" + active.size() + " 条），请先停嘱";
        }
        return null;
    }
}
