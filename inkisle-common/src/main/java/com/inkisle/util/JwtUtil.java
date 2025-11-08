package com.inkisle.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 的签发与解析
 * 登录成功后签发 token 返回前端，后续请求携带该 token，
 * 拦截器调 parse 验证签名，同时取出用户信息
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours}")
    private long expireHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token。subject 存放用户主键，username 与 role 写入 claim，
     * 拦截器不用再查库就知道是谁、什么角色。
     * ver 是这个账号当前的身份版本号，改密、登出、封禁之后自增，
     * 旧 token 的 ver 对不上就失效了
     */
    public String generate(Long userId, String username, String role) {
        return generate(userId, username, role, "0");
    }

    public String generate(Long userId, String username, String role, String version) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireHours * 60 * 60 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("ver", version == null ? "0" : version)
                .issuedAt(now)
                .expiration(expire)
                .signWith(key())
                .compact();
    }

    /**
     * 解析 token。签名不合法、过期、格式错误均会抛异常，
     * 统一捕获后返回 null，调用方判空处理
     */
    public Claims parse(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parse(token);
        return claims == null ? null : Long.valueOf(claims.getSubject());
    }

}
