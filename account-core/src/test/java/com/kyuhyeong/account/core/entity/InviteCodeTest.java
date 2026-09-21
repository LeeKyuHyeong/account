package com.kyuhyeong.account.core.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InviteCode#isValid} — 만료·취소 판정 (DB 불필요 순수 단위).
 */
class InviteCodeTest {

    private static final LocalDateTime EXPIRES = LocalDateTime.of(2026, 9, 24, 12, 0);

    private static InviteCode code(LocalDateTime expiresAt) {
        return InviteCode.issue(Household.builder().id(1L).build(), User.builder().id(1L).build(), "ABCD2345", expiresAt);
    }

    @Test
    @DisplayName("만료 시각 전이면 유효")
    void validBeforeExpiry() {
        assertThat(code(EXPIRES).isValid(EXPIRES.minusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("만료 시각이 된 순간부터 무효")
    void invalidAtExactExpiry() {
        assertThat(code(EXPIRES).isValid(EXPIRES)).isFalse();
        assertThat(code(EXPIRES).isValid(EXPIRES.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("무기한(기존 발급분)은 만료되지 않는다")
    void nullExpiryNeverExpires() {
        assertThat(code(null).isValid(LocalDateTime.of(2100, 1, 1, 0, 0))).isTrue();
    }

    @Test
    @DisplayName("취소하면 만료 전이어도 무효")
    void revokedIsInvalid() {
        InviteCode ic = code(EXPIRES);
        ic.revoke();
        assertThat(ic.isValid(EXPIRES.minusDays(1))).isFalse();
    }
}
