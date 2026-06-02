package com.kyuhyeong.account.api.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 세션 인증 경로 (/web/**) 의 principal — 카카오 OAuth2 단독.
 *
 * <p>매 요청마다 DB 를 다시 치지 않도록 활성 가구 ID + 역할을 principal 에 동봉하고,
 * {@link SessionHouseholdContextFilter} 가 이 값으로 {@code HouseholdContext} 를 채운다.
 *
 * <p>{@code activeHouseholdId} 가 null 이면 아직 어느 가구에도 속하지 않은 상태
 * (가입 직후) — 권한이 비어 있고, 필터가 {@code /web/onboarding} 으로 유도한다.
 *
 * <p>{@link OAuth2User} 구현 — oauth2Login 이 본 객체를 principal 로 HttpSession 에 저장한다.
 * Serializable — 세션 직렬화.
 */
public final class AccountPrincipal implements OAuth2User, Serializable {

    private final Long userId;
    private final Long activeHouseholdId;
    private final String role;
    private final String nickname;
    private final Map<String, Object> attributes;

    public AccountPrincipal(Long userId,
                            Long activeHouseholdId,
                            String role,
                            String nickname,
                            Map<String, Object> attributes) {
        this.userId = userId;
        this.activeHouseholdId = activeHouseholdId;
        this.role = role;
        this.nickname = nickname;
        this.attributes = attributes;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getActiveHouseholdId() {
        return activeHouseholdId;
    }

    public String getRole() {
        return role;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 가구 미가입(온보딩 전) 은 권한 없음 — OWNER/MEMBER 게이트에 걸리지 않고 온보딩으로만 유도.
        return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /** OAuth2User 식별자 — userId 문자열. (이메일/username 개념 없음.) */
    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
