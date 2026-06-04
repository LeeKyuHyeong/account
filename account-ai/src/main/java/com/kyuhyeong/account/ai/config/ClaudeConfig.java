package com.kyuhyeong.account.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Claude API 호출용 {@link RestClient} 구성.
 *
 * <p>Spring Boot 3.2+의 {@link RestClient}는 동기식이지만 Java 21 가상 스레드와
 * 자연스럽게 호환되어 별도 reactive 스택 없이 다수 동시 요청 처리 가능.
 * Spring Boot의 {@code spring.threads.virtual.enabled=true} 옵션과 함께 사용.
 *
 * <p>API 키는 모든 요청에 자동 부착되며, 호출처(클라이언트 코드)는 신경 쓸 필요 없음.
 */
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfig {

    /** Anthropic API 인증 헤더명. */
    private static final String API_KEY_HEADER = "x-api-key";

    /** Anthropic API 버전 헤더명. */
    private static final String API_VERSION_HEADER = "anthropic-version";

    @Bean
    public RestClient claudeRestClient(ClaudeProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 연결 수립은 짧게(기본 5초) — 네트워크 단절 시 read 타임아웃까지 매달리지 않게 분리.
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.timeout().toMillis());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(API_KEY_HEADER, properties.apiKey())
                .defaultHeader(API_VERSION_HEADER, properties.apiVersion())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }
}
