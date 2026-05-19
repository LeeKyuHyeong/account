package com.kyuhyeong.account.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Access / refresh JWT 발급 + 검증.
 *
 * <p>Access 클레임 구조:
 * <ul>
 *   <li>{@code sub} : user_id (String 으로 직렬화)</li>
 *   <li>{@code household_id} : 활성 가구 ID (Long)</li>
 *   <li>{@code role} : 활성 가구 내 역할 ("OWNER" | "MEMBER")</li>
 * </ul>
 *
 * <p>Refresh 클레임은 {@code sub} + {@code type=refresh} 만 — access 갱신 전용.
 */
@Component
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties props;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        if (props.secret() == null || props.secret().isBlank()) {
            throw new IllegalStateException(
                    "account.jwt.secret is not set — paste a base64 random into application-secret.yml "
                            + "or export ACCOUNT_JWT_SECRET env var (see application-secret.yml.example).");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(props.secret());
        } catch (DecodingException e) {
            throw new IllegalStateException("account.jwt.secret must be base64 encoded", e);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "account.jwt.secret must decode to at least 32 bytes (HS256 minimum). "
                            + "Got " + keyBytes.length + " bytes.");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String issueAccessToken(Long userId, Long householdId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("household_id", householdId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTtl())))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String issueRefreshToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.refreshTtl())))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 서명 검증 후 access 토큰 클레임 추출. 검증 실패 시 {@link JwtException} 전파.
     */
    public AccessClaims parseAccess(String token) {
        Claims c = Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token)
                .getPayload();
        return new AccessClaims(
                Long.parseLong(c.getSubject()),
                c.get("household_id", Long.class),
                c.get("role", String.class)
        );
    }

    /**
     * Refresh 토큰 클레임 추출 + {@code type=refresh} 검증.
     */
    public Long parseRefreshUserId(String token) {
        Claims c = Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token)
                .getPayload();
        if (!"refresh".equals(c.get("type", String.class))) {
            throw new JwtException("Not a refresh token");
        }
        return Long.parseLong(c.getSubject());
    }

    public record AccessClaims(Long userId, Long householdId, String role) {
    }
}
