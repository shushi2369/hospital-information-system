package com.his.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 会话（key=sess:{userId}，TTL 30 分钟，访问滑动续期）。
 * 修改密码/禁用用户时删除会话实现即时失效（《04》§5）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionService {
    private static final String KEY_PREFIX = "sess:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public void save(LoginUser user) {
        try {
            redis.opsForValue().set(KEY_PREFIX + user.getUserId(), objectMapper.writeValueAsString(user), TTL);
        } catch (Exception e) {
            log.error("会话写入失败", e);
            throw new BizException(ErrorCode.C9001, "会话写入失败");
        }
    }

    /** 会话不存在返回 null（由过滤器按未登录处理） */
    public LoginUser get(Long userId) {
        String key = KEY_PREFIX + userId;
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            redis.expire(key, TTL);
            return objectMapper.readValue(json, LoginUser.class);
        } catch (Exception e) {
            log.error("会话读取失败", e);
            return null;
        }
    }

    public void remove(Long userId) {
        try {
            redis.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.error("会话删除失败", e);
        }
    }
}
