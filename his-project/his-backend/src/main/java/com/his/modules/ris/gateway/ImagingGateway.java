package com.his.modules.ris.gateway;

/** 影像设备防腐层（对齐 LabInstrumentGateway 模式）：真实 DICOM/HL7 对接仅替换实现。 */
public interface ImagingGateway {

    /**
     * 按检查大类/部位/申请号生成确定性影像研究（Mock）。
     *
     * @param modality   1 DR 2 CT 3 MR
     * @param bodyPart   检查部位
     * @param requestNo  申请单号（决定性哈希种子）
     * @return 影像元数据与 Mock 所见；criticalSign 非空表示命中危急征象
     */
    MockStudy mockStudy(int modality, String bodyPart, String requestNo);

    record MockStudy(int seriesCount, int imageCount, String impressionText, String criticalSign) {
    }
}
