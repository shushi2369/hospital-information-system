package com.his.modules.system.app;

import com.his.modules.system.entity.SysUser;
import com.his.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限审计模块跨模块应用服务：仅暴露用户名解析（单据显示收费员/操作人姓名用）。
 */
@Service
@RequiredArgsConstructor
public class SystemAppService {
    private final SysUserMapper userMapper;

    public String getUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getRealName();
    }

    public Map<Long, String> getUsernameMap(Collection<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        for (SysUser user : userMapper.selectBatchIds(userIds)) {
            result.put(user.getId(), user.getRealName());
        }
        return result;
    }

    /** 排班下拉选项：仅 id/姓名/账号，避免把 sys_user 其他字段带出 */
    public List<Map<String, Object>> nurseOptions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser user : userMapper.selectNurseUsers()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("username", user.getUsername());
            row.put("realName", user.getRealName());
            result.add(row);
        }
        return result;
    }

    public boolean isNurse(Long userId) {
        return userId != null && userMapper.countNurseRole(userId) > 0;
    }
}
