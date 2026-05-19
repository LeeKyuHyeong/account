package com.kyuhyeong.account.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 발급 / 검증 설정 (account.jwt.*).
 *
 * <p>{@code secret} 은 base64 인코딩된 32 바이트 이상의 랜덤 값 (HS256 권장 키 길이).
 * 시크릿 분리 정책 (§10.2): application-secret.yml 또는 환경변수 {@code ACCOUNT_JWT_SECRET}
 * 으로 주입. 빈 값이면 {@link JwtTokenProvider#init()} 에서 명확히 실패시킨다.
 */
@ConfigurationProperties(prefix = "account.jwt")
public record JwtProperties(
        String secret,
        Duration accessTtl,
        Duration refreshTtl
) {
    public JwtProperties {
        if (accessTtl == null) {
            accessTtl = Duration.ofMinutes(15);
        }
        if (refreshTtl == null) {
            refreshTtl = Duration.ofDays(30);
        }
    }
}
