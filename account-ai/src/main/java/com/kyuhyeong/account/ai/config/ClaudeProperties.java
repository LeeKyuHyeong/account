package com.kyuhyeong.account.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Claude API 호출 관련 설정.
 *
 * <p>{@code application.yml}의 {@code account.claude.*} 키에 바인딩.
 * API 키는 절대 평문 커밋 금지 — {@code application-secret.properties}에 분리하거나
 * 환경변수 {@code ACCOUNT_CLAUDE_API_KEY}로 주입.
 *
 * @param apiKey         Claude Console에서 발급한 API 키
 * @param model          사용할 모델 ID (예: "claude-sonnet-4-6", "claude-haiku-4-5")
 * @param maxTokens      응답 최대 토큰 — items 가 길면 1024로 잘리는 사례가 있어 2048 기본
 * @param timeout        응답 읽기 타임아웃 — 영수증 1장 분석은 보통 5~15초
 * @param connectTimeout 연결 수립 타임아웃 — 네트워크 단절 시 read 타임아웃(30초)까지
 *                       매달리지 않도록 짧게 분리
 * @param maxRetries     429/529/5xx 일시 장애 재시도 횟수 (0 = 재시도 없음)
 * @param retryBackoff   재시도 간 대기 기본값 — n번째 재시도는 n배 대기 (선형 백오프)
 * @param baseUrl        API 베이스 URL (기본값으로 두고, 프록시·테스트 시에만 오버라이드)
 * @param apiVersion     Anthropic API 버전 헤더 값
 */
@ConfigurationProperties(prefix = "account.claude")
public record ClaudeProperties(
        String apiKey,
        String model,
        int maxTokens,
        Duration timeout,
        Duration connectTimeout,
        Integer maxRetries,
        Duration retryBackoff,
        String baseUrl,
        String apiVersion
) {

    public ClaudeProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "account.claude.api-key must be set (env: ACCOUNT_CLAUDE_API_KEY)");
        }
        if (model == null || model.isBlank()) {
            model = "claude-sonnet-4-6";
        }
        if (maxTokens <= 0) {
            maxTokens = 2048;
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(30);
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (maxRetries == null || maxRetries < 0) {
            maxRetries = 2;
        }
        if (retryBackoff == null) {
            retryBackoff = Duration.ofSeconds(1);
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.anthropic.com";
        }
        if (apiVersion == null || apiVersion.isBlank()) {
            apiVersion = "2023-06-01";
        }
    }
}
