package com.his.modules.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.basedata.dto.DoctorCreateRequest;
import com.his.modules.basedata.dto.DoctorQueryRequest;
import com.his.modules.basedata.dto.DoctorUpdateRequest;
import com.his.modules.basedata.entity.Department;
import com.his.modules.basedata.entity.Doctor;
import com.his.modules.basedata.mapper.DepartmentMapper;
import com.his.modules.basedata.mapper.DoctorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 医生服务（D-06/D-07/D-08）。
 * 业务约束（《02 数据库设计》§4.3）：user_id 与登录账号 1:1 绑定后不可改；
 * 所属科室必须是启用的临床科室（dept_type=1 且 status=1）。
 */
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;

    /**
     * D-06 医生列表：按科室/是否专家/状态过滤，按 id 升序。
     */
    public List<Doctor> list(DoctorQueryRequest query) {
        return doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                .eq(query.getDeptId() != null, Doctor::getDeptId, query.getDeptId())
                .eq(query.getIsExpert() != null, Doctor::getIsExpert, query.getIsExpert())
                .eq(query.getStatus() != null, Doctor::getStatus, query.getStatus())
                .orderByAsc(Doctor::getId));
    }

    /**
     * D-07 新增医生：登录账号 1:1 绑定唯一、工号唯一、所属科室必须为启用临床科室。
     */
    @Transactional(rollbackFor = Exception.class)
    public Doctor create(DoctorCreateRequest request) {
        checkDeptClinical(request.getDeptId());
        Long userBound = doctorMapper.selectCount(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getUserId, request.getUserId()));
        if (userBound != null && userBound > 0) {
            throw new BizException(ErrorCode.B5004, "医生已绑定登录账号");
        }
        Long codeExists = doctorMapper.selectCount(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getDoctorCode, request.getDoctorCode()));
        if (codeExists != null && codeExists > 0) {
            throw new BizException(ErrorCode.B5001, "医生编码已存在");
        }
        Doctor doctor = new Doctor();
        doctor.setUserId(request.getUserId());
        doctor.setDeptId(request.getDeptId());
        doctor.setDoctorCode(request.getDoctorCode());
        doctor.setDoctorName(request.getDoctorName());
        doctor.setTitle(request.getTitle());
        doctor.setIsExpert(request.getIsExpert() != null ? request.getIsExpert() : 0);
        doctor.setNormalFee(request.getNormalFee());
        doctor.setExpertFee(request.getExpertFee());
        doctor.setDailyQuota(request.getDailyQuota());
        doctor.setStatus(1);
        doctorMapper.insert(doctor);
        return doctor;
    }

    /**
     * D-08 修改医生：userId/doctorCode 不可改；未传字段不修改（如仅停用只传 status=0）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Doctor update(Long id, DoctorUpdateRequest request) {
        Doctor doctor = doctorMapper.selectById(id);
        if (doctor == null) {
            throw new BizException(ErrorCode.A0001, "记录不存在");
        }
        checkStatus(request.getStatus());
        if (request.getDeptId() != null) {
            checkDeptClinical(request.getDeptId());
            doctor.setDeptId(request.getDeptId());
        }
        doctor.setDoctorName(request.getDoctorName());
        doctor.setTitle(request.getTitle());
        if (request.getIsExpert() != null) {
            doctor.setIsExpert(request.getIsExpert());
        }
        if (request.getNormalFee() != null) {
            doctor.setNormalFee(request.getNormalFee());
        }
        if (request.getExpertFee() != null) {
            doctor.setExpertFee(request.getExpertFee());
        }
        if (request.getDailyQuota() != null) {
            doctor.setDailyQuota(request.getDailyQuota());
        }
        if (request.getStatus() != null) {
            doctor.setStatus(request.getStatus());
        }
        doctorMapper.updateById(doctor);
        return doctor;
    }

    /**
     * 科室必须是 dept_type=1 的启用科室，否则提示"必须选择临床科室"。
     */
    private void checkDeptClinical(Long deptId) {
        Department dept = departmentMapper.selectById(deptId);
        if (dept == null || dept.getDeptType() == null || dept.getDeptType() != 1
                || dept.getStatus() == null || dept.getStatus() != 1) {
            throw new BizException(ErrorCode.A0001, "必须选择临床科室");
        }
    }

    private static void checkStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BizException(ErrorCode.A0001, "状态取值只能为 1（启用）或 0（停用）");
        }
    }
}
