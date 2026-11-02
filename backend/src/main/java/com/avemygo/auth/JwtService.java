package com.avemygo.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * JWT 签发与校验。
 * 密钥/有效期来自 .env（JWT_SECRET / JWT_EXPIRATION_HOURS），不入代码库。
 * 职责只有两个：generate 签发通行证，parse 验真并取出载荷。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationHours;

    public JwtService(@Value("${JWT_SECRET}") String secret,
                      @Value("${JWT_EXPIRATION_HOURS:24}") long expirationHours) {
        // HS256 要求密钥至少 32 字节
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    /**
     * 不带令牌版本的签发（测试与兼容用）。生产登录一律走三参版本，
     * 这样封禁/降权才能对已签发的令牌立即生效。
     */
    public String generate(Long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /**
     * 登录成功后调用：签发 token（载荷 = 用户id + 角色 + 令牌版本 + 过期时间）。
     * 令牌版本与数据库中的 user.token_version 不一致时，过滤器会判定为失效——
     * 这样封禁/降权能立刻生效，不必维护黑名单。
     */
    public String generate(Long userId, String role, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))       // sub = 谁的通行证
                .claim("role", role)                   // 自定义载荷：权限角色
                .claim("ver", tokenVersion)             // 令牌版本
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(key)                         // 用密钥签名，防伪造
                .compact();
    }

    /** 校验并解析 token：签名错/过期会抛 JwtException，由调用方决定怎么拒绝 */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
