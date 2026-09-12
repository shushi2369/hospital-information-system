package com.his.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.PageQuery;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.system.dto.LoginLogQuery;
import com.his.modules.system.dto.OperationLogQuery;
import com.his.modules.system.entity.SysLoginLog;
import com.his.modules.system.entity.SysOperationLog;
import com.his.modules.system.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志查询接口（S-10~S-11，只读）。
 */
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogController {
    private final AuditQueryService auditQueryService;

    @GetMapping("/operations")
    @PreAuthorize("@ss.hasPerm('sys:log:query')")
    public R<PageResult<SysOperationLog>> operations(OperationLogQuery query) {
        Page<SysOperationLog> page = auditQueryService.pageOperationLog(query, query.getUsername(),
                query.getModule(), query.getBizId(), query.getStartDate(), query.getEndDate());
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/logins")
    @PreAuthorize("@ss.hasPerm('sys:log:query')")
    public R<PageResult<SysLoginLog>> logins(LoginLogQuery query) {
        Page<SysLoginLog> page = auditQueryService.pageLoginLog(query, query.getUsername(),
                query.getSuccess(), query.getStartDate(), query.getEndDate());
        return R.ok(PageResult.of(page));
    }
}
