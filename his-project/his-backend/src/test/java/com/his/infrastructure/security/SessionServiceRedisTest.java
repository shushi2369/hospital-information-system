package com.his.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 Redis 验证原子脚本；运行时传入 -Dhis.test.redis=true，可用 his.test.redis.port 指定端口。 */
@EnabledIfSystemProperty(named = "his.test.redis", matches = "true")
class SessionServiceRedisTest {
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private SessionService sessions;
    private Long userId;

    @BeforeEach
    void setUp() {
        connection = new LettuceConnectionFactory("127.0.0.1", Integer.getInteger("his.test.redis.port", 6379));
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        sessions = new SessionService(redis, new ObjectMapper());
        userId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }

    @AfterEach
    void tearDown() {
        if (redis != null) redis.delete("sess:" + userId);
        if (connection != null) connection.destroy();
    }

    @Test
    void repeatedAndConcurrentLoginsKeepOneActiveGeneration() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> logins = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                logins.add(() -> {
                    LoginUser user = user();
                    sessions.save(user);
                    return user.getSessionId();
                });
            }
            var results = executor.invokeAll(logins);
            String sessionId = results.get(0).get();
            for (var result : results) assertEquals(sessionId, result.get());
            assertNotNull(sessions.get(userId, sessionId));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void revokeThenLoginChangesGenerationAndRejectsOldToken() {
        LoginUser old = user();
        sessions.save(old);
        sessions.removeStrict(userId);
        LoginUser current = user();
        sessions.save(current);
        assertNotEquals(old.getSessionId(), current.getSessionId());
        assertNull(sessions.get(userId, old.getSessionId()));
        assertNotNull(sessions.get(userId, current.getSessionId()));
    }

    @Test
    void sessionSavePreservesEmptyPermissionAndRoleCollections() {
        LoginUser user = user();
        user.setPermissions(Set.of());
        user.setRoleIds(Set.of());
        user.setRoleCodes(Set.of());
        sessions.save(user);
        LoginUser restored = sessions.get(userId, user.getSessionId());
        assertNotNull(restored);
        assertEquals(Set.of(), restored.getPermissions());
        assertEquals(Set.of(), restored.getRoleIds());
        assertEquals(Set.of(), restored.getRoleCodes());
    }

    @Test
    void legacySessionGetsFreshGenerationOnLogin() throws Exception {
        redis.opsForValue().set("sess:" + userId, new ObjectMapper().writeValueAsString(user()), Duration.ofMinutes(1));
        LoginUser user = user();
        sessions.save(user);
        assertNotNull(user.getSessionId());
        assertNotNull(sessions.get(userId, user.getSessionId()));
    }

    private LoginUser user() {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUsername("测试用户");
        user.setPermissions(Set.of("system:read"));
        user.setRoleCodes(Set.of("TEST"));
        user.setRoleIds(Set.of(2L));
        return user;
    }
}
