package com.his.modules.lis.gateway;

import java.util.List;

/** 检验仪器防腐层（对齐医保/支付网关模式）：真实 ASTM/HL7 对接仅替换实现。 */
public interface LabInstrumentGateway {

    /** 按标本号生成确定性结果行（Mock） */
    List<ResultRow> fetchResult(String specimenNo);

    record ResultRow(String itemName, String resultValue, String unit,
                     String referenceRange, int abnormalFlag) {
    }
}
