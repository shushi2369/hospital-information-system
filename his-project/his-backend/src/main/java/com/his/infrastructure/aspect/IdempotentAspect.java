package com.his.infrastructure.aspect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.Idempotent;
import com.his.common.R;
import com.his.infrastructure.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 幂等拦截（《01》§6.4，对应时序图《03》§4.3）：
 * SETNX idem:{userId}:{key}=请求摘要(TTL 24h)，重复请求返回首次响应快照；摘要不同返回 A0005；
 * Redis 不可用降级放行，由数据库唯一约束兜底。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    public static final String HEADER = "X-Idempotency-Key";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return pjp.proceed();
        }
        String key = attrs.getRequest().getHeader(HEADER);
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.A0004);
        }

        Long userId;
        try {
            userId = CurrentUser.id();
        } catch (Exception e) {
            userId = 0L;
        }
        String redisKey = "idem:" + userId + ":" + key;
        String digest = digest(pjp.getArgs());

        Boolean first;
        try {
            first = redis.opsForValue().setIfAbsent(redisKey, digest, TTL);
        } catch (Exception e) {
            log.warn("幂等组件不可用，降级放行（由数据库唯一约束兜底）");
            return pjp.proceed();
        }

        if (Boolean.FALSE.equals(first)) {
            String stored = redis.opsForValue().get(redisKey);
            if (stored != null && !digest.equals(stored)) {
                throw new BizException(ErrorCode.A0005);
            }
            String snapshot = redis.opsForValue().get(redisKey + ":resp");
            if (snapshot != null) {
                return objectMapper.readValue(snapshot, new TypeReference<R<Object>>() {
                });
            }
            throw new BizException(ErrorCode.A0005, "相同请求正在处理中，请勿重复提交");
        }

        Object result = pjp.proceed();
        try {
            if (result instanceof R<?> r) {
                redis.opsForValue().set(redisKey + ":resp", objectMapper.writeValueAsString(r), TTL);
            }
        } catch (Exception e) {
            log.warn("幂等响应快照写入失败", e);
        }
        return result;
    }

    private String digest(Object[] args) {
        try {
            String json = objectMapper.writeValueAsString(args == null ? "[]" : args);
            byte[] d = MessageDigest.getInstance("SHA-256").digest(json.getBytes());
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            return "digest-error";
        }
    }
}
