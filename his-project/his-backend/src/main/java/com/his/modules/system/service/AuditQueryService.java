package com.his.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.infrastructure.util.LikeEscapeUtil;
import com.his.common.ErrorCode;
import com.his.common.PageQuery;
import com.his.infrastructure.security.SessionService;
import com.his.modules.system.entity.SysLoginLog;
import com.his.modules.system.entity.SysOperationLog;
import com.his.modules.system.mapper.SysLoginLogMapper;
import com.his.modules.system.mapper.SysOperationLogMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 审计日志查询服务（append-only，只提供写入与查询，不提供删除/修改）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    @Getter
    private final SysOperationLogMapper operationLogMapper;
    @Getter
    private final SysLoginLogMapper loginLogMapper;

    /** 异步落库：业务线程零阻塞；队列满时由 auditExecutor 的 CallerRuns 策略退化为同步 */
    @Async("auditExecutor")
    public void saveOperationLog(SysOperationLog entity) {
        try {
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("审计日志异步落库失败 biz={}/{}", entity.getModule(), entity.getAction(), e);
        }
    }

    public Page<SysOperationLog> pageOperationLog(PageQuery query, String username, String module,
                                                  String bizId, java.time.LocalDate startDate,
                                                  java.time.LocalDate endDate) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .likeRight(username != null && !username.isBlank(), SysOperationLog::getUsername, LikeEscapeUtil.escape(username))
                .eq(module != null && !module.isBlank(), SysOperationLog::getModule, module)
                .eq(bizId != null && !bizId.isBlank(), SysOperationLog::getBizId, bizId)
                .ge(startDate != null, SysOperationLog::getCreatedAt, startDate == null ? null : startDate.atStartOfDay())
                .le(endDate != null, SysOperationLog::getCreatedAt, endDate == null ? null : endDate.atTime(23, 59, 59))
                .orderByDesc(SysOperationLog::getCreatedAt);
        return operationLogMapper.selectPage(query.toPage(), wrapper);
    }

    public Page<SysLoginLog> pageLoginLog(PageQuery query, String username, Integer success,
                                          java.time.LocalDate startDate, java.time.LocalDate endDate) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .likeRight(username != null && !username.isBlank(), SysLoginLog::getUsername, LikeEscapeUtil.escape(username))
                .eq(success != null, SysLoginLog::getSuccess, success)
                .ge(startDate != null, SysLoginLog::getCreatedAt, startDate == null ? null : startDate.atStartOfDay())
                .le(endDate != null, SysLoginLog::getCreatedAt, endDate == null ? null : endDate.atTime(23, 59, 59))
                .orderByDesc(SysLoginLog::getCreatedAt);
        return loginLogMapper.selectPage(query.toPage(), wrapper);
    }
}
