package com.his.modules.lis.gateway;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock 检验仪器：按标本号哈希生成确定性结果（末位 9 时白细胞危急偏高，供闭环演练）。
 */
@Component
public class MockLabInstrumentGateway implements LabInstrumentGateway {

    @Override
    public List<ResultRow> fetchResult(String specimenNo) {
        int h = Math.abs(specimenNo.hashCode());
        boolean critical = specimenNo.endsWith("9");
        double wbc = critical ? 35.6 : 4.0 + (h % 40) / 10.0;
        return List.of(
                new ResultRow("白细胞计数", fmt(wbc), "10^9/L", "3.5~9.5", wbc > 30 ? 1 : (wbc < 3.5 ? 2 : 0)),
                new ResultRow("红细胞计数", fmt(4.0 + (h % 10) / 10.0), "10^12/L", "4.3~5.8", 0),
                new ResultRow("血红蛋白", fmt(130 + (h % 30)), "g/L", "130~175", 0),
                new ResultRow("血小板计数", fmt(150 + (h % 100)), "10^9/L", "125~350", 0),
                new ResultRow("C反应蛋白", fmt(1 + (h % 8)), "mg/L", "0~10", 0));
    }

    private String fmt(double v) {
        return java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
