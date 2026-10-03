package com.his.infrastructure.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 五十一轮：单号发号器降级与恢复补偿。
 * 场景：正常发号（Redis INCR 1、2）→ Redis 部分降级（超时抛异常）→ 实例内高基址号段（500001 起）
 * → Redis 恢复但计数回退（重启后 INCR 从 1 重新开始）→ 补偿跳过降级号段，全程无撞号。
 */
class IdGeneratorTest {

    @SuppressWarnings("unchecked")
    @Test
    void degradedSegmentAndRecoveryCompensation_neverCollide() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(vo);

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        IdGenerator gen = new IdGenerator(redis);

        // 正常期：INCR 依次 1、2 → 降级期：INCR 抛异常 → 恢复期：计数回退，INCR 从 1 重新开始
        when(vo.increment(anyString())).thenReturn(1L, 2L)
                .thenThrow(new RuntimeException("redis timeout"))
                .thenReturn(1L);
        // 补偿跳号：increment(key, delta) = 本地最大值(500001) + 1
        when(vo.increment(anyString(), anyLong())).thenReturn(500002L);

        String n1 = gen.next("SF");
        String n2 = gen.next("SF");
        String n3 = gen.next("SF");   // 降级号段：500001
        String n4 = gen.next("SF");   // 恢复补偿：跳到 500002

        assertEquals("SF" + date + "000001", n1);
        assertEquals("SF" + date + "000002", n2);
        assertEquals("SF" + date + "500001", n3);
        assertEquals("SF" + date + "500002", n4);
        // 四个号两两不同（降级段与恢复段无撞号）
        assertEquals(4, Set.of(n1, n2, n3, n4).size());
    }
}
