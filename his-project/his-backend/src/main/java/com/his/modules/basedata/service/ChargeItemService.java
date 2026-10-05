package com.his.modules.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.basedata.dto.ChargeItemCreateRequest;
import com.his.modules.basedata.dto.ChargeItemQueryRequest;
import com.his.modules.basedata.dto.ChargeItemUpdateRequest;
import com.his.modules.basedata.entity.ChargeItem;
import com.his.modules.basedata.mapper.ChargeItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收费项目服务（D-12/D-13/D-14）。
 * 调价说明：price 变更直接更新本表并记操作审计；历史单据依赖计费快照，本期无需调价单。
 */
@Service
@RequiredArgsConstructor
public class ChargeItemService {

    private static final String DEFAULT_UNIT = "次";

    private final ChargeItemMapper chargeItemMapper;

    /**
     * D-12 收费项目列表：按类别/状态过滤，按 id 升序。
     */
    public List<ChargeItem> list(ChargeItemQueryRequest query) {
        return chargeItemMapper.selectList(new LambdaQueryWrapper<ChargeItem>()
                .eq(query.getCategory() != null, ChargeItem::getCategory, query.getCategory())
                .eq(query.getStatus() != null, ChargeItem::getStatus, query.getStatus())
                .orderByAsc(ChargeItem::getId));
    }

    /**
     * D-13 新增收费项目：项目编码唯一。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChargeItem create(ChargeItemCreateRequest request) {
        checkStatus(request.getStatus());
        checkCodeUnique(request.getItemCode());
        ChargeItem item = new ChargeItem();
        item.setItemCode(request.getItemCode());
        item.setItemName(request.getItemName());
        item.setCategory(request.getCategory());
        item.setPrice(request.getPrice());
        item.setUnit(request.getUnit() != null && !request.getUnit().isBlank() ? request.getUnit() : DEFAULT_UNIT);
        item.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        try {
            chargeItemMapper.insert(item);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.B5001);
        }
        return item;
    }

    /**
     * D-14 修改/调价/停用收费项目：编码不可改；未传字段不修改。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChargeItem update(Long id, ChargeItemUpdateRequest request) {
        ChargeItem item = chargeItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.A0001, "记录不存在");
        }
        checkStatus(request.getStatus());
        item.setItemName(request.getItemName());
        if (request.getCategory() != null) {
            item.setCategory(request.getCategory());
        }
        if (request.getPrice() != null) {
            item.setPrice(request.getPrice());
        }
        if (request.getUnit() != null) {
            item.setUnit(request.getUnit());
        }
        if (request.getStatus() != null) {
            item.setStatus(request.getStatus());
        }
        chargeItemMapper.updateById(item);
        return item;
    }

    private void checkCodeUnique(String itemCode) {
        Long count = chargeItemMapper.selectCount(new LambdaQueryWrapper<ChargeItem>()
                .eq(ChargeItem::getItemCode, itemCode));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.B5001, "项目编码已存在");
        }
    }

    private static void checkStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BizException(ErrorCode.A0001, "状态取值只能为 1（启用）或 0（停用）");
        }
    }
}
