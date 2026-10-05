package com.his.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与验签（HS256，payload 仅 userId/username，《01》§6.1）。
 */
@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expireMinutes;

    public JwtUtil(@Value("${his.jwt.secret}") String secret,
                   @Value("${his.jwt.expire-minutes}") long expireMinutes) {
        // 仓库公开的 dev 默认值原样进生产 = 任何人可离线伪造任意身份的合法令牌（八十六轮安全审计）
        if ("dev-only-jwt-secret-please-change-0123456789abcdef".equals(secret)) {
            System.err.println("==================================================================");
            System.err.println("[SECURITY-WARN] JWT_SECRET 仍在使用仓库默认值：令牌可被任何拿到仓库的人伪造。");
            System.err.println("  生产部署必须通过 JWT_SECRET 环境变量注入强随机值（>=32 字符）。");
            System.err.println("==================================================================");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    public String generate(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMinutes * 60_000))
                .signWith(key)
                .compact();
    }

    /** 解析并校验签名与有效期；非法令牌抛 JwtException */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
