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

    /** 登录成功后调用：签发 token（载荷 = 用户id + 角色 + 过期时间） */
    public String generate(Long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))       // sub = 谁的通行证
                .claim("role", role)                   // 自定义载荷：权限角色
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
