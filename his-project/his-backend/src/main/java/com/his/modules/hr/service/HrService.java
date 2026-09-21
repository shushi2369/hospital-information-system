package com.his.modules.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.hr.dto.*;
import com.his.modules.hr.entity.HrStaff;
import com.his.modules.hr.entity.HrTitleChange;
import com.his.modules.hr.mapper.HrStaffMapper;
import com.his.modules.hr.mapper.HrTitleChangeMapper;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HR 人事服务（《18》§2）：建档 → 职称变更（留痕）→ 离职。与 sys_user 弱关联（user_id 可空）。
 */
@Service
@RequiredArgsConstructor
public class HrService {
    private final HrStaffMapper staffMapper;
    private final HrTitleChangeMapper titleChangeMapper;
    private final PltService pltService;
    private final IdGenerator idGenerator;

    /** 员工分页（H-01） */
    public PageResult<HrStaff> page(HrStaffQuery query) {
        Page<HrStaff> page = staffMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<HrStaff>()
                        .eq(query.getDeptId() != null, HrStaff::getDeptId, query.getDeptId())
                        .eq(query.getStatus() != null, HrStaff::getStatus, query.getStatus())
                        .like(query.getName() != null && !query.getName().isBlank(),
                                HrStaff::getName, query.getName())
                        .orderByDesc(HrStaff::getId));
        return PageResult.of(page);
    }

    /** 详情（H-02，含职称变更史） */
    public Map<String, Object> detail(Long id) {
        HrStaff staff = requireStaff(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("staff", staff);
        result.put("titleChanges", titleChangeMapper.selectList(
                new LambdaQueryWrapper<HrTitleChange>()
                        .eq(HrTitleChange::getStaffId, id)
                        .orderByDesc(HrTitleChange::getEffectiveDate)));
        return result;
    }

    /** 建档（H-03） */
    @Transactional
    public String create(StaffUpsertRequest req) {
        HrStaff staff = new HrStaff();
        staff.setStaffNo(idGenerator.next("YG"));
        applyUpsert(staff, req);
        staff.setStatus(1);
        staffMapper.insert(staff);
        return staff.getStaffNo();
    }

    /** 更新（H-04） */
    @Transactional
    public void update(Long id, StaffUpsertRequest req) {
        HrStaff staff = requireStaff(id);
        applyUpsert(staff, req);
        if (staffMapper.updateById(staff) != 1) {
            throw new BizException(ErrorCode.A0001, "档案已变化，请刷新后重试");
        }
    }

    /** 离职登记（H-05）：记录离职日期并停用 */
    @Transactional
    public void exit(Long id) {
        HrStaff staff = requireStaff(id);
        if (staff.getStatus() == 0) {
            throw new BizException(ErrorCode.A0001, "该员工已离职");
        }
        staff.setStatus(0);
        staff.setExitDate(LocalDate.now());
        if (staffMapper.updateById(staff) != 1) {
            throw new BizException(ErrorCode.A0001, "档案已变化，请刷新后重试");
        }
        pltService.recordEvent("hr.staff.exited", staff.getStaffNo(), "{}");
    }

    /** 职称变更（H-06）：档案更新 + 变更记录留痕 */
    @Transactional
    public Long titleChange(Long id, TitleChangeRequest req) {
        HrStaff staff = requireStaff(id);
        if (staff.getStatus() != 1) {
            throw new BizException(ErrorCode.A0001, "离职员工不可变更职称");
        }
        if (staff.getTitle().equals(req.getNewTitle())) {
            throw new BizException(ErrorCode.A0001, "新职称与现职称相同");
        }
        HrTitleChange change = new HrTitleChange();
        change.setStaffId(id);
        change.setOldTitle(staff.getTitle());
        change.setNewTitle(req.getNewTitle());
        change.setEffectiveDate(req.getEffectiveDate());
        change.setApproverId(CurrentUser.id());
        change.setNote(req.getNote());
        titleChangeMapper.insert(change);
        staff.setTitle(req.getNewTitle());
        if (staffMapper.updateById(staff) != 1) {
            throw new BizException(ErrorCode.A0001, "档案已变化，请刷新后重试");
        }
        pltService.recordEvent("hr.staff.titleChanged", staff.getStaffNo(),
                "{\"from\":\"" + change.getOldTitle() + "\",\"to\":\"" + req.getNewTitle() + "\"}");
        return change.getId();
    }

    private HrStaff requireStaff(Long id) {
        HrStaff staff = staffMapper.selectById(id);
        if (staff == null) {
            throw new BizException(ErrorCode.A0001, "员工档案不存在");
        }
        return staff;
    }

    private void applyUpsert(HrStaff staff, StaffUpsertRequest req) {
        staff.setName(req.getName());
        staff.setDeptId(req.getDeptId());
        staff.setTitle(req.getTitle());
        staff.setLicenseNo(req.getLicenseNo());
        staff.setPhone(req.getPhone());
        staff.setEntryDate(req.getEntryDate());
        staff.setUserId(req.getUserId());
    }
}
