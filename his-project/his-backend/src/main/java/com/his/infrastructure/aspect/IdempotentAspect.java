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
 * SETNX idem:{userId}:{method}:{URI}:{key}=请求摘要(TTL 24h)，重复请求返回首次响应快照；
 * 摘要不同返回 A0005 并释放旧键；Redis 不可用降级为进程内短期锁（S-06），DB 唯一约束最终兜底。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {
    /** 一百一十四轮 S-06：Redis 降级时的进程内幂等锁（单机兜底，5 分钟过期） */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LOCAL_LOCKS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long LOCAL_LOCK_TTL_MS = 5 * 60 * 1000L;


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
        // 一百一十四轮 S-08：键掺入方法+URI——原仅 userId+header，跨接口复用同一 key
        // 会互相污染（A0005 残留致自我可用性故障 / 参数恰同返回错误端点的快照）
        String redisKey = "idem:" + userId + ":" + attrs.getRequest().getMethod()
                + ":" + attrs.getRequest().getRequestURI() + ":" + key;
        String digest = digest(pjp.getArgs());

        Boolean first;
        try {
            first = redis.opsForValue().setIfAbsent(redisKey, digest, TTL);
        } catch (Exception e) {
            // 一百一十四轮 S-06：降级不再全量放行——进程内短期锁（5 分钟）兜住无唯一约束的
            // 单机重复提交；多实例部署下降级仍有跨实例窗口（教学单机部署可接受），并打点告警
            log.warn("幂等组件不可用，降级为进程内短期锁（数据库唯一约束仍为最终兜底）: {}", e.getMessage());
            long now = System.currentTimeMillis();
            Long prev = LOCAL_LOCKS.putIfAbsent(redisKey, now + LOCAL_LOCK_TTL_MS);
            boolean acquired = prev == null || prev < now;
            if (acquired) {
                LOCAL_LOCKS.put(redisKey, now + LOCAL_LOCK_TTL_MS);
            }
            if (!acquired) {
                throw new BizException(ErrorCode.A0005, "相同请求正在处理中，请勿重复提交（降级模式）");
            }
            try {
                return pjp.proceed();
            } finally {
                if (LOCAL_LOCKS.get(redisKey) != null && LOCAL_LOCKS.get(redisKey) <= now + LOCAL_LOCK_TTL_MS) {
                    LOCAL_LOCKS.remove(redisKey);
                }
            }
        }

        if (Boolean.FALSE.equals(first)) {
            String stored = redis.opsForValue().get(redisKey);
            if (stored != null && !digest.equals(stored)) {
                // 一百一十四轮 S-08：摘要不匹配（同 key 复用不同内容）时删除旧键——
                // 原 A0005 拒绝但键残留 TTL，期间该用户此接口被同 key 自我锁死
                redis.delete(redisKey);
                throw new BizException(ErrorCode.A0005);
            }
            String snapshot = redis.opsForValue().get(redisKey + ":resp");
            if (snapshot != null) {
                return objectMapper.readValue(snapshot, new TypeReference<R<Object>>() {
                });
            }
            throw new BizException(ErrorCode.A0005, "相同请求正在处理中，请勿重复提交");
        }

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
            // 业务/系统失败释放幂等锁：失败请求不产生幂等语义，允许用户重试
            // （不释放则失败 key 残留 TTL 时长，同 key 重试永远 A0005——查验二十五轮）
            redis.delete(redisKey);
            throw e;
        }
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
