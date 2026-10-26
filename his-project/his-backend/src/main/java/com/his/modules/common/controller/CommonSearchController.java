package com.his.modules.common.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.R;
import com.his.infrastructure.util.LikeEscapeUtil;
import com.his.modules.basedata.entity.Doctor;
import com.his.modules.basedata.mapper.DoctorMapper;
import com.his.modules.patient.entity.PatPatient;
import com.his.modules.patient.mapper.PatPatientMapper;
import com.his.modules.system.entity.SysUser;
import com.his.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用检索（一百五十八轮：裸 ID 输入家族总解）——供各工作台选择器组件使用。
 * 安全口径：默认"已登录即可"（SecurityConfig anyRequest().authenticated()），
 * 只返回选择器必需的最小字段（id/姓名/编号/性别），不暴露证件/联系方式等 PII；
 * 患者完整档案仍走 patient:archive:query 权限控制的 /patients。
 */
@RestController
@RequestMapping("/api/v1/common")
@RequiredArgsConstructor
public class CommonSearchController {

    private final PatPatientMapper patientMapper;
    private final SysUserMapper userMapper;
    private final DoctorMapper doctorMapper;

    /** 患者模糊检索（姓名/建档号前缀），最小字段 */
    @GetMapping("/patient-search")
    public R<List<Map<String, Object>>> patientSearch(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BizException(ErrorCode.A0001, "检索关键词不能为空");
        }
        String kw = LikeEscapeUtil.escape(keyword.trim());
        List<PatPatient> rows = patientMapper.selectList(new LambdaQueryWrapper<PatPatient>()
                .eq(PatPatient::getStatus, 1)
                .and(w -> w.likeRight(PatPatient::getName, kw)
                        .or().likeRight(PatPatient::getPatientNo, kw))
                .orderByDesc(PatPatient::getId)
                .last("LIMIT 20"));
        List<Map<String, Object>> out = rows.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("patientNo", p.getPatientNo());
            m.put("gender", p.getGender());
            return m;
        }).toList();
        return R.ok(out);
    }

    /** 在职人员检索（取血护士/核查第二签名/输血核对等 sys_user 场景），最小字段 */
    @GetMapping("/staff-search")
    public R<List<Map<String, Object>>> staffSearch(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BizException(ErrorCode.A0001, "检索关键词不能为空");
        }
        String kw = LikeEscapeUtil.escape(keyword.trim());
        List<SysUser> rows = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .and(w -> w.likeRight(SysUser::getRealName, kw)
                        .or().likeRight(SysUser::getUsername, kw))
                .orderByAsc(SysUser::getId)
                .last("LIMIT 20"));
        List<Map<String, Object>> out = rows.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getRealName());
            m.put("username", u.getUsername());
            return m;
        }).toList();
        return R.ok(out);
    }

    /** 启用医生检索（排台主刀等 bas_doctor 场景），最小字段 */
    @GetMapping("/doctor-search")
    public R<List<Map<String, Object>>> doctorSearch(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BizException(ErrorCode.A0001, "检索关键词不能为空");
        }
        String kw = LikeEscapeUtil.escape(keyword.trim());
        List<Doctor> rows = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getStatus, 1)
                .and(w -> w.likeRight(Doctor::getDoctorName, kw)
                        .or().likeRight(Doctor::getDoctorCode, kw))
                .orderByAsc(Doctor::getId)
                .last("LIMIT 20"));
        List<Map<String, Object>> out = rows.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("name", d.getDoctorName());
            m.put("title", d.getTitle());
            return m;
        }).toList();
        return R.ok(out);
    }
}
