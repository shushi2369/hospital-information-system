package com.his.modules.system.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.common.AuditLog;
import com.his.common.BizException;
import com.his.common.TraceContext;
import com.his.common.util.MaskUtil;
import com.his.infrastructure.security.CurrentUser;
import com.his.modules.system.entity.SysOperationLog;
import com.his.modules.system.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作留痕切面（@AuditLog → sys_operation_log，append-only）。
 * 参数经 MaskUtil 脱敏后落库；切面自身异常只记错误日志，不影响业务。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {
    private final AuditQueryService auditQueryService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        String resultCode = "OK";
        try {
            return pjp.proceed();
        } catch (BizException e) {
            resultCode = e.getErrorCode().getCode();
            throw e;
        } catch (Throwable e) {
            resultCode = "C9001";
            throw e;
        } finally {
            save(pjp, auditLog, resultCode, System.currentTimeMillis() - start);
        }
    }

    private void save(ProceedingJoinPoint pjp, AuditLog auditLog, String resultCode, long costMs) {
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            SysOperationLog entity = new SysOperationLog();
            entity.setTraceId(TraceContext.traceId());
            try {
                entity.setUserId(CurrentUser.id());
                entity.setUsername(CurrentUser.get().getUsername());
            } catch (Exception e) {
                entity.setUserId(0L);
                entity.setUsername("anonymous");
            }
            entity.setModule(auditLog.module());
            entity.setAction(auditLog.action());
            entity.setBizType(auditLog.bizType());
            entity.setMethod(signature.getDeclaringTypeName() + "#" + signature.getName());
            try {
                entity.setParamsJson(MaskUtil.maskJson(objectMapper.writeValueAsString(pjp.getArgs())));
            } catch (Exception e) {
                entity.setParamsJson("(serialize-failed)");
            }
            entity.setResultCode(resultCode);
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                entity.setIp(attrs.getRequest().getRemoteAddr());
                String ua = attrs.getRequest().getHeader("User-Agent");
                entity.setUserAgent(ua != null && ua.length() > 256 ? ua.substring(0, 256) : ua);
            }
            entity.setCostMs((int) costMs);
            auditQueryService.saveOperationLog(entity);
        } catch (Exception e) {
            log.error("审计日志写入失败", e);
        }
    }
}
