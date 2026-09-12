package com.his.infrastructure.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单号生成（《01》§6.6）：{业务前缀}{yyyyMMdd}{6位当日序号}，Redis INCR 计数；
 * Redis 不可用时降级为实例内序号（单机演示可用，集群部署必须恢复 Redis）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdGenerator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;
    private final Map<String, AtomicLong> fallback = new ConcurrentHashMap<>();

    public String next(String bizPrefix) {
        String date = LocalDate.now().format(DATE);
        String key = "seq:" + bizPrefix + ":" + date;
        try {
            Long seq = redis.opsForValue().increment(key);
            redis.expire(key, Duration.ofDays(7));
            if (seq != null) {
                return bizPrefix + date + String.format("%06d", seq);
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，单号降级为实例内序号");
        }
        long seq = fallback.computeIfAbsent(bizPrefix + date, k -> new AtomicLong()).incrementAndGet();
        return bizPrefix + date + String.format("%06d", seq);
    }
}
