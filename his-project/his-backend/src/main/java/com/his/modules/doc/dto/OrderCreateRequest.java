package com.his.modules.doc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OrderCreateRequest {
    @NotNull(message = "住院不能为空")
    private Long admissionId;
    @NotNull(message = "医嘱类别不能为空")
    @Min(value = 1, message = "医嘱类别取值 1长期/2临时")
    @Max(value = 2, message = "医嘱类别取值 1长期/2临时")
    private Integer orderClass;
    @NotNull(message = "医嘱分类不能为空")
    @Min(value = 1, message = "分类取值 1药品/2检查/3检验/4治疗/5护理/6材料")
    @Max(value = 6, message = "分类取值 1药品/2检查/3检验/4治疗/5护理/6材料")
    private Integer category;
    @Size(max = 16, message = "用药频次最长 16 字")
    private String frequency;
    private Boolean skinTestFlag;
    @NotEmpty(message = "医嘱明细不能为空")
    private List<OrderItemRequest> items;
}
