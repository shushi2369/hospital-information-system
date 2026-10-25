package com.his.modules.basedata.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.basedata.dto.DoctorCreateRequest;
import com.his.modules.basedata.dto.DoctorUpdateRequest;
import com.his.modules.basedata.entity.Department;
import com.his.modules.basedata.entity.Doctor;
import com.his.modules.basedata.mapper.DepartmentMapper;
import com.his.modules.basedata.mapper.DoctorMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** DoctorService 单测：临床科室门禁 + 账号/编码双重查重 + 部分字段更新口径。 */
class DoctorServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Doctor.class);
    }

    private final DoctorMapper doctorMapper = mock(DoctorMapper.class);
    private final DepartmentMapper departmentMapper = mock(DepartmentMapper.class);

    private DoctorService svc() {
        return new DoctorService(doctorMapper, departmentMapper);
    }

    private Department dept(Integer deptType, int status) {
        Department d = new Department();
        d.setId(3L);
        d.setDeptType(deptType);
        d.setStatus(status);
        return d;
    }

    private DoctorCreateRequest createReq() {
        DoctorCreateRequest r = new DoctorCreateRequest();
        r.setUserId(66L);
        r.setDeptId(3L);
        r.setDoctorCode("D001");
        r.setDoctorName("张三");
        r.setNormalFee(new BigDecimal("30"));
        return r;
    }

    @Test
    void create_deptNotClinical_throws() {
        when(departmentMapper.selectById(3L)).thenReturn(dept(2, 1));
        BizException e = assertThrows(BizException.class, () -> svc().create(createReq()));
        assertTrue(e.getMessage().contains("临床科室"));
    }

    @Test
    void create_deptDisabled_throws() {
        when(departmentMapper.selectById(3L)).thenReturn(dept(1, 0));
        BizException e = assertThrows(BizException.class, () -> svc().create(createReq()));
        assertTrue(e.getMessage().contains("临床科室"));
    }

    @Test
    void create_userBound_throws() {
        when(departmentMapper.selectById(3L)).thenReturn(dept(1, 1));
        when(doctorMapper.selectCount(any())).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> svc().create(createReq()));
        assertEquals(ErrorCode.B5004, e.getErrorCode());
    }

    @Test
    void create_codeDuplicate_throws() {
        when(departmentMapper.selectById(3L)).thenReturn(dept(1, 1));
        when(doctorMapper.selectCount(any())).thenReturn(0L).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> svc().create(createReq()));
        assertEquals(ErrorCode.B5001, e.getErrorCode());
    }

    @Test
    void create_happy_defaultsExpert0Status1() {
        when(departmentMapper.selectById(3L)).thenReturn(dept(1, 1));
        when(doctorMapper.selectCount(any())).thenReturn(0L);

        Doctor created = svc().create(createReq());

        assertEquals(1, created.getStatus());
        assertEquals(0, created.getIsExpert());
        assertEquals(66L, created.getUserId());
    }

    @Test
    void create_raceDuplicateKey_throws() {
        when(departmentMapper.selectById(3L)).thenReturn(dept(1, 1));
        when(doctorMapper.selectCount(any())).thenReturn(0L);
        when(doctorMapper.insert(any(Doctor.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk"));
        BizException e = assertThrows(BizException.class, () -> svc().create(createReq()));
        assertTrue(e.getMessage().contains("已绑定"));
    }

    @Test
    void update_invalidStatus_throws() {
        when(doctorMapper.selectById(1L)).thenReturn(new Doctor());
        DoctorUpdateRequest req = new DoctorUpdateRequest();
        req.setStatus(2);
        BizException e = assertThrows(BizException.class, () -> svc().update(1L, req));
        assertTrue(e.getMessage().contains("状态取值"));
    }

    @Test
    void update_deptSwitchRechecksClinical() {
        Doctor existing = new Doctor();
        existing.setId(1L);
        existing.setDeptId(3L);
        when(doctorMapper.selectById(1L)).thenReturn(existing);
        when(departmentMapper.selectById(9L)).thenReturn(dept(2, 1));
        DoctorUpdateRequest req = new DoctorUpdateRequest();
        req.setDeptId(9L);
        BizException e = assertThrows(BizException.class, () -> svc().update(1L, req));
        assertTrue(e.getMessage().contains("临床科室"));
    }

    @Test
    void update_happy_appliesOnlyProvidedFields() {
        Doctor existing = new Doctor();
        existing.setId(1L);
        existing.setDeptId(3L);
        existing.setIsExpert(0);
        existing.setNormalFee(new BigDecimal("30"));
        when(doctorMapper.selectById(1L)).thenReturn(existing);
        DoctorUpdateRequest req = new DoctorUpdateRequest();
        req.setDoctorName("李四");
        req.setNormalFee(new BigDecimal("50"));

        Doctor updated = svc().update(1L, req);

        assertEquals("李四", updated.getDoctorName());
        assertEquals(0, new BigDecimal("50").compareTo(updated.getNormalFee()));
        assertEquals(0, updated.getIsExpert());
        verify(doctorMapper).updateById(existing);
    }
}
