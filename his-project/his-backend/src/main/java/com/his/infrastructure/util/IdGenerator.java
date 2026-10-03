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
 * Redis 部分降级（超时/抖动）时切换实例内号段（基址 500000 起，与正常号段物理隔离，防止与宕机前历史号撞号）；
 * Redis 恢复后若计数回退（重启丢失），补偿跳过降级号段再继续（五十一轮）。
 * 注意：会话在 Redis（SessionService），全量宕机时请求无法通过认证，本降级路径仅在部分降级场景可达；
 * 单机演示可用，集群部署必须恢复 Redis。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdGenerator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 降级号段基址：正常日发号量为百级，降级段占据 500001~999999，与正常号段不相交 */
    private static final long DEGRADED_BASE = 500_000L;

    private final StringRedisTemplate redis;
    private final Map<String, AtomicLong> fallback = new ConcurrentHashMap<>();

    public String next(String bizPrefix) {
        String date = LocalDate.now().format(DATE);
        String redisKey = "seq:" + bizPrefix + ":" + date;
        String localKey = bizPrefix + date;
        try {
            Long seq = redis.opsForValue().increment(redisKey);
            redis.expire(redisKey, Duration.ofDays(7));
            if (seq != null) {
                long value = seq;
                AtomicLong local = fallback.get(localKey);
                if (local != null && value <= local.get()) {
                    // 恢复期补偿：Redis 计数落后于降级期本地已发号段，一次性跳到本地最大值之后
                    // （多实例部署时补偿为近似值，集群以恢复 Redis 数据为准）
                    value = redis.opsForValue().increment(redisKey, local.get() - value + 1);
                    redis.expire(redisKey, Duration.ofDays(7));
                }
                if (local != null) {
                    final long seen = value;
                    local.updateAndGet(n -> Math.max(n, seen));
                } else {
                    fallback.put(localKey, new AtomicLong(value));
                }
                return bizPrefix + date + String.format("%06d", value);
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，单号降级为实例内号段（基址 {}）", DEGRADED_BASE);
        }
        // 降级号段强制从基址之后起步（正常期已同步的本地计数可能远低于基址，必须跳过）
        long seq = fallback.computeIfAbsent(localKey, k -> new AtomicLong(DEGRADED_BASE))
                .updateAndGet(n -> Math.max(n, DEGRADED_BASE) + 1);
        return bizPrefix + date + String.format("%06d", seq);
    }
}
