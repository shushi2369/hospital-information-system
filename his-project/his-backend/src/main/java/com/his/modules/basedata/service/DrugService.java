package com.his.modules.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.infrastructure.util.LikeEscapeUtil;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.modules.basedata.dto.DrugCreateRequest;
import com.his.modules.basedata.dto.DrugQueryRequest;
import com.his.modules.basedata.dto.DrugUpdateRequest;
import com.his.modules.basedata.entity.Drug;
import com.his.modules.basedata.mapper.DrugMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 药品服务（D-09/D-10/D-11）。
 * 调价说明：retail_price 变更直接更新本表并记操作审计；历史处方依赖单价快照（cli_prescription_item.unit_price），本期无需调价单。
 */
@Service
@RequiredArgsConstructor
public class DrugService {

    private final DrugMapper drugMapper;

    /**
     * D-09 药品分页查询：名称/编码模糊、分类/状态精确，按 id 升序。
     */
    public PageResult<Drug> page(DrugQueryRequest query) {
        LambdaQueryWrapper<Drug> wrapper = new LambdaQueryWrapper<Drug>()
                .like(StringUtils.hasText(query.getDrugName()), Drug::getDrugName, LikeEscapeUtil.escape(query.getDrugName()))
                .like(StringUtils.hasText(query.getDrugCode()), Drug::getDrugCode, LikeEscapeUtil.escape(query.getDrugCode()))
                .eq(query.getCategory() != null, Drug::getCategory, query.getCategory())
                .eq(query.getStatus() != null, Drug::getStatus, query.getStatus())
                .orderByAsc(Drug::getId);
        Page<Drug> page = drugMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    /**
     * D-10 新增药品：药品编码唯一。
     */
    @Transactional(rollbackFor = Exception.class)
    public Drug create(DrugCreateRequest request) {
        checkStatus(request.getStatus());
        checkCodeUnique(request.getDrugCode());
        Drug drug = new Drug();
        drug.setDrugCode(request.getDrugCode());
        drug.setDrugName(request.getDrugName());
        drug.setGenericName(request.getGenericName());
        drug.setSpec(request.getSpec());
        drug.setDosageForm(request.getDosageForm());
        drug.setCategory(request.getCategory());
        drug.setManufacturer(request.getManufacturer());
        drug.setUnit(request.getUnit());
        drug.setRetailPrice(request.getRetailPrice());
        drug.setStockWarningQty(request.getStockWarningQty() != null ? request.getStockWarningQty() : BigDecimal.ZERO);
        drug.setIsAntibiotic(request.getIsAntibiotic() != null ? request.getIsAntibiotic() : 0);
        drug.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        try {
            drugMapper.insert(drug);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.A0001, "药品编码已存在");
        }
        return drug;
    }

    /**
     * D-11 修改/调价/停用药品：编码不可改；未传字段不修改。
     */
    @Transactional(rollbackFor = Exception.class)
    public Drug update(Long id, DrugUpdateRequest request) {
        Drug drug = drugMapper.selectById(id);
        if (drug == null) {
            throw new BizException(ErrorCode.A0001, "记录不存在");
        }
        checkStatus(request.getStatus());
        drug.setDrugName(request.getDrugName());
        if (request.getGenericName() != null) {
            drug.setGenericName(request.getGenericName());
        }
        drug.setSpec(request.getSpec());
        if (request.getDosageForm() != null) {
            drug.setDosageForm(request.getDosageForm());
        }
        if (request.getCategory() != null) {
            drug.setCategory(request.getCategory());
        }
        if (request.getManufacturer() != null) {
            drug.setManufacturer(request.getManufacturer());
        }
        drug.setUnit(request.getUnit());
        if (request.getRetailPrice() != null) {
            drug.setRetailPrice(request.getRetailPrice());
        }
        if (request.getStockWarningQty() != null) {
            drug.setStockWarningQty(request.getStockWarningQty());
        }
        if (request.getIsAntibiotic() != null) {
            drug.setIsAntibiotic(request.getIsAntibiotic());
        }
        if (request.getStatus() != null) {
            drug.setStatus(request.getStatus());
        }
        drugMapper.updateById(drug);
        return drug;
    }

    private void checkCodeUnique(String drugCode) {
        Long count = drugMapper.selectCount(new LambdaQueryWrapper<Drug>()
                .eq(Drug::getDrugCode, drugCode));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.B5001, "药品编码已存在");
        }
    }

    private static void checkStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BizException(ErrorCode.A0001, "状态取值只能为 1（启用）或 0（停用）");
        }
    }
}
