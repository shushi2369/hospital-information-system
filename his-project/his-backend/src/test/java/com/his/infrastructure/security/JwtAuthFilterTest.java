package com.his.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 撤销前的令牌不能借同账户的新会话恢复访问；多端令牌可共享当前会话。 */
class JwtAuthFilterTest {
    private static final String SECRET = "test-session-secret-0123456789abcdef0123456789";
    private final ObjectMapper mapper = new ObjectMapper();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        filter = new JwtAuthFilter(new JwtUtil(SECRET, 30), new SessionService(redis, mapper));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentSessionAcceptsTokensFromMultipleClients() throws Exception {
        storeSession("current-session");
        assertNotNull(authenticate(token("current-session", "client-one")));
        assertNotNull(authenticate(token("current-session", "client-two")));
    }

    @Test
    void revokedTokenCannotReviveAfterRelogin() throws Exception {
        String oldToken = token("old-session", "client-one");
        storeSession("old-session");
        assertNotNull(authenticate(oldToken));
        when(values.get("sess:7")).thenReturn(null);
        assertNull(authenticate(oldToken));
        storeSession("new-session");
        assertNull(authenticate(oldToken), "重新登录后，撤销前的令牌仍应被拒绝");
        assertNotNull(authenticate(token("new-session", "client-two")));
    }

    @Test
    void rejectedTokenDoesNotExtendReplacementSession() throws Exception {
        storeSession("new-session");
        assertNull(authenticate(token("old-session", "client-one")));
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void legacyTokenWithoutSessionIdentifierIsRejected() throws Exception {
        storeSession("new-session");
        assertNull(authenticate(token(null, "legacy-client")));
    }

    @Test
    void legacyRedisSessionCannotAuthenticateNewToken() throws Exception {
        storeSession(null);
        assertNull(authenticate(token("new-session", "client-one")));
    }

    private void storeSession(String sessionId) throws Exception {
        LoginUser user = new LoginUser();
        user.setUserId(7L);
        user.setUsername("nurse.wang");
        user.setSessionId(sessionId);
        user.setPermissions(Set.of("nur:vital:read"));
        when(values.get("sess:7")).thenReturn(mapper.writeValueAsString(user));
    }

    private String token(String sessionId, String client) {
        return Jwts.builder().subject("7").id(client).claim("sid", sessionId)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private Authentication authenticate(String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
