package com.his.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 六十四轮：会话读取容错分级——续期失败不杀会话（滑动 TTL 丢失是小损失），
 * 读取失败/无会话才按未登录处理。
 */
class SessionServiceTest {

    @SuppressWarnings("unchecked")
    private SessionService service(ValueOperations<String, String> vo) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(vo);
        return new SessionService(redis, new ObjectMapper());
    }

    private String sessionJson() throws Exception {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setUsername("admin");
        user.setRoleCodes(java.util.Set.of("ADMIN"));
        return new ObjectMapper().writeValueAsString(user);
    }

    @Test
    void get_expireFailureStillReturnsUser() throws Exception {
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(vo.get("sess:1")).thenReturn(sessionJson());
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(vo);
        doThrow(new RuntimeException("redis busy")).when(redis).expire(anyString(), any());

        SessionService service = new SessionService(redis, new ObjectMapper());
        LoginUser user = service.get(1L);

        assertNotNull(user);
        assertEquals(1L, user.getUserId());
    }

    private void assertEquals(long expected, Long actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual.longValue());
    }

    @Test
    void get_missingSessionReturnsNull() {
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        SessionService service = service(vo);
        when(vo.get("sess:1")).thenReturn(null);

        assertNull(service.get(1L));
    }

    @Test
    void get_readFailureReturnsNull() throws Exception {
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(vo.get("sess:1")).thenThrow(new RuntimeException("connection reset"));

        assertNull(service(vo).get(1L));
    }
}
