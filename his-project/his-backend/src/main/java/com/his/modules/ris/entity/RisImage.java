package com.his.modules.ris.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code ris_image} 影像 Mock 元数据（真实 DICOM 为后续实现位） */
@Getter
@Setter
@TableName("ris_image")
public class RisImage extends BaseEntity {
    private String studyNo;
    private Long requestId;
    private Integer seriesCount;
    private Integer imageCount;
    private String impressionText;
    private Integer status;

    @Version
    private Integer version;
}
