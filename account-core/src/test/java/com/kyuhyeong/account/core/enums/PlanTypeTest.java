package com.kyuhyeong.account.core.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구독 Phase 1 — 티어별 영수증 AI 월 한도 매핑 검증 (DB 불필요 순수 단위).
 */
class PlanTypeTest {

    @Test
    void freeHasFiniteQuota() {
        assertThat(PlanType.FREE.monthlyReceiptQuota()).isEqualTo(10);
        assertThat(PlanType.FREE.isUnlimitedReceipts()).isFalse();
    }

    @Test
    void familyHasLargerQuotaThanFree() {
        assertThat(PlanType.FAMILY.monthlyReceiptQuota()).isEqualTo(100);
        assertThat(PlanType.FAMILY.monthlyReceiptQuota())
                .isGreaterThan(PlanType.FREE.monthlyReceiptQuota());
        assertThat(PlanType.FAMILY.isUnlimitedReceipts()).isFalse();
    }

    @Test
    void proIsUnlimited() {
        assertThat(PlanType.PRO.monthlyReceiptQuota()).isEqualTo(PlanType.UNLIMITED);
        assertThat(PlanType.PRO.isUnlimitedReceipts()).isTrue();
    }

    @Test
    void everyTierHasDisplayName() {
        for (PlanType type : PlanType.values()) {
            assertThat(type.displayName()).isNotBlank();
        }
    }
}
