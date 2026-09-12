package com.his.modules.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.basedata.dto.DepartmentCreateRequest;
import com.his.modules.basedata.dto.DepartmentQueryRequest;
import com.his.modules.basedata.dto.DepartmentUpdateRequest;
import com.his.modules.basedata.entity.Department;
import com.his.modules.basedata.mapper.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 科室服务（D-03/D-04/D-05）。
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;

    /**
     * D-03 科室列表：按类型/状态过滤，按 id 升序。
     */
    public List<Department> list(DepartmentQueryRequest query) {
        return departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .eq(query.getDeptType() != null, Department::getDeptType, query.getDeptType())
                .eq(query.getStatus() != null, Department::getStatus, query.getStatus())
                .orderByAsc(Department::getId));
    }

    /**
     * D-04 新增科室：科室编码唯一。
     */
    @Transactional(rollbackFor = Exception.class)
    public Department create(DepartmentCreateRequest request) {
        checkStatus(request.getStatus());
        checkCodeUnique(request.getDeptCode());
        Department dept = new Department();
        dept.setOrgId(request.getOrgId());
        dept.setDeptCode(request.getDeptCode());
        dept.setDeptName(request.getDeptName());
        dept.setDeptType(request.getDeptType());
        dept.setLocation(request.getLocation());
        dept.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        departmentMapper.insert(dept);
        return dept;
    }

    /**
     * D-05 修改/停用科室：编码不可改；未传字段不修改。
     */
    @Transactional(rollbackFor = Exception.class)
    public Department update(Long id, DepartmentUpdateRequest request) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.A0001, "记录不存在");
        }
        checkStatus(request.getStatus());
        dept.setOrgId(request.getOrgId() != null ? request.getOrgId() : dept.getOrgId());
        dept.setDeptName(request.getDeptName());
        if (request.getDeptType() != null) {
            dept.setDeptType(request.getDeptType());
        }
        if (request.getLocation() != null) {
            dept.setLocation(request.getLocation());
        }
        if (request.getStatus() != null) {
            dept.setStatus(request.getStatus());
        }
        departmentMapper.updateById(dept);
        return dept;
    }

    private void checkCodeUnique(String deptCode) {
        Long count = departmentMapper.selectCount(new LambdaQueryWrapper<Department>()
                .eq(Department::getDeptCode, deptCode));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.B5001, "科室编码已存在");
        }
    }

    private static void checkStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BizException(ErrorCode.A0001, "状态取值只能为 1（启用）或 0（停用）");
        }
    }
}
