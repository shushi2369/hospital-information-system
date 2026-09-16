package com.his.modules.inp.spi;

/**
 * 出院前置检查钩子（依赖倒置：inp 定义接口，医嘱/检查等模块实现，
 * 避免 inp → doc/clinic 的反向依赖）。返回 null 表示无阻断，否则返回阻断原因。
 */
public interface DischargeCheckHook {
    String checkBlocker(Long admissionId);
}
