package com.his.modules.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.basedata.dto.OrganizationUpdateRequest;
import com.his.modules.basedata.entity.Organization;
import com.his.modules.basedata.mapper.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织机构服务（D-01/D-02）。
 * 一期单机构：查询返回 id 最小的一条记录（种子数据仅一条），编码 orgCode 不可修改。
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationMapper organizationMapper;

    /**
     * D-01 机构信息查询：返回 id 最小的一条；未初始化时返回 null。
     */
    public Organization get() {
        return organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .orderByAsc(Organization::getId)
                .last("LIMIT 1"));
    }

    /**
     * D-02 机构信息维护：按 id 最小的一条更新（编码不可改）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Organization update(OrganizationUpdateRequest request) {
        Organization org = get();
        if (org == null) {
            throw new BizException(ErrorCode.A0001, "记录不存在");
        }
        checkStatus(request.getStatus());
        org.setOrgName(request.getOrgName());
        org.setAddress(request.getAddress());
        org.setPhone(request.getPhone());
        if (request.getStatus() != null) {
            org.setStatus(request.getStatus());
        }
        organizationMapper.updateById(org);
        return org;
    }

    private static void checkStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BizException(ErrorCode.A0001, "状态取值只能为 1（启用）或 0（停用）");
        }
    }
}
