package com.kyuhyeong.account.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AccountPrincipal} 단위 테스트 — sysAdmin 플래그에 따른 ROLE_SYSADMIN 부여.
 *
 * <p>앱 관리자는 카카오 providerUserId 화이트리스트로 결정되며 가구 role(OWNER/MEMBER) 과
 * 독립이다 — 가구 role 권한을 대체하지 않고 추가로만 부여돼야 한다.
 */
class AccountPrincipalTest {

    private static List<String> authorities(String role, boolean sysAdmin) {
        AccountPrincipal principal = new AccountPrincipal(
                1L, role == null ? null : 10L, role, "닉네임", sysAdmin, Map.of("id", 12345L));
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Test
    @DisplayName("sysAdmin=true + OWNER — ROLE_OWNER 와 ROLE_SYSADMIN 모두 보유")
    void ownerSysAdminHasBothRoles() {
        assertThat(authorities("OWNER", true))
                .containsExactlyInAnyOrder("ROLE_OWNER", "ROLE_SYSADMIN");
    }

    @Test
    @DisplayName("sysAdmin=false — 기존 가구 role 권한만 (ROLE_SYSADMIN 없음)")
    void nonSysAdminKeepsHouseholdRoleOnly() {
        assertThat(authorities("MEMBER", false)).containsExactly("ROLE_MEMBER");
    }

    @Test
    @DisplayName("role=null(온보딩 전) + sysAdmin=true — ROLE_SYSADMIN 만")
    void onboardingSysAdminHasOnlySysAdminRole() {
        assertThat(authorities(null, true)).containsExactly("ROLE_SYSADMIN");
    }

    @Test
    @DisplayName("role=null + sysAdmin=false — 권한 없음 (온보딩 유도, 기존 동작 유지)")
    void onboardingNonAdminHasNoAuthorities() {
        assertThat(authorities(null, false)).isEmpty();
    }
}
