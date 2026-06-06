package com.kyuhyeong.account.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 앱 관리자(시스템 관리) 화이트리스트 — 카카오 providerUserId 기준.
 *
 * <p>{@code account.admin.kakao-ids} (env {@code ACCOUNT_ADMIN_KAKAO_IDS} 콤마 구분 또는
 * application-secret.yml) 에서 바인딩. 비어 있으면 관리자 없음 — {@code /web/sysadmin} 은
 * 아무도 접근 불가.
 *
 * <p>가구 role(OWNER/MEMBER) 과 독립인 앱 전역 권한이다. 로그인 시
 * {@link KakaoOAuth2UserService} 가 본 화이트리스트를 체크해 {@link AccountPrincipal} 에
 * sysAdmin 플래그를 동봉 → {@code ROLE_SYSADMIN} authority 로 표준 Security 게이트를 탄다.
 * (DB 컬럼 대신 설정값 — 관리자가 본인 1명인 현 단계에서 마이그레이션 없이 최소로.)
 */
@Component
@ConfigurationProperties(prefix = "account.admin")
public class SysAdminProperties {

    /** Set 프로퍼티는 setter 없이 getter 가 반환하는 가변 컬렉션에 Spring 이 바인딩한다. */
    private final Set<String> kakaoIds = new HashSet<>();

    public Set<String> getKakaoIds() {
        return kakaoIds;
    }
}
