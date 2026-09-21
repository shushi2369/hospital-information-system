package com.his.modules.ris.gateway;

import org.springframework.stereotype.Component;

/**
 * Mock 影像设备：按申请号哈希生成确定性影像元数据与所见文本；
 * 申请号末位为 9 时命中危急征象（主动脉夹层征象/大量脑出血），供危急值 source=2 闭环演练。
 */
@Component
public class MockImagingGateway implements ImagingGateway {

    @Override
    public MockStudy mockStudy(int modality, String bodyPart, String requestNo) {
        int h = Math.abs(requestNo.hashCode());
        boolean critical = requestNo.endsWith("9");
        String criticalSign = null;
        String finding;
        int series = 1 + h % 3;
        int images;
        switch (modality) {
            case 2 -> {
                images = 60 + h % 40;
                finding = (bodyPart == null || bodyPart.isBlank() ? "胸部" : bodyPart)
                        + "CT 平扫：诸层面序贯显示，" + (critical ? "主动脉弓旁见内膜片影，对比剂外渗征象。" : "肺纹理清晰，纵隔居中，未见明显占位。");
                if (critical) {
                    criticalSign = "主动脉夹层征象";
                }
            }
            case 3 -> {
                images = 120 + h % 60;
                finding = (bodyPart == null || bodyPart.isBlank() ? "头颅" : bodyPart)
                        + "MR 平扫：脑实质信号" + (critical ? "见大片长 T2 信号，中线移位。" : "未见明显异常。");
                if (critical) {
                    criticalSign = "大面积脑梗死征象";
                }
            }
            default -> {
                images = 2 + h % 3;
                finding = (bodyPart == null || bodyPart.isBlank() ? "胸部" : bodyPart)
                        + "DR：双肺" + (critical ? "见大片实变影，心影增大。" : "纹理清晰，心影大小正常，膈面光滑。");
                if (critical) {
                    criticalSign = "张力性气胸征象";
                }
            }
        }
        return new MockStudy(series, images, finding, criticalSign);
    }
}
