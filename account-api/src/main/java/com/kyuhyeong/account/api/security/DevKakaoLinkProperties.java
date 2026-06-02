package com.kyuhyeong.account.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 로컬 개발 전용 — 첫 카카오 로그인을 기존 시드 유저에 연결하는 매핑.
 *
 * <p>{@code account.dev.kakao-links} (application-secret.yml, gitignored) 에서 바인딩.
 * key = 카카오 providerUserId, value = 연결할 시드 유저 email.
 *
 * <p>운영에서는 비워둔다 → 매핑이 없으면 {@link KakaoOAuth2UserService} 가 신규 유저를 만들고
 * 온보딩으로 보낸다. (CLAUDE.md: application-dev.yml 분리 금지 — 속성으로만 게이팅.)
 */
@Component
@ConfigurationProperties(prefix = "account.dev")
public class DevKakaoLinkProperties {

    /** Map 프로퍼티는 setter 없이 getter 가 반환하는 가변 맵에 Spring 이 바인딩한다. */
    private final Map<String, String> kakaoLinks = new HashMap<>();

    public Map<String, String> getKakaoLinks() {
        return kakaoLinks;
    }
}
