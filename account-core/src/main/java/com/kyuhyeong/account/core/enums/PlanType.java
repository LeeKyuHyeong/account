package com.kyuhyeong.account.core.enums;

/**
 * 가구의 플랜 티어 (docs/account.md §6.1 households.plan_type).
 *
 * <p>구독 Phase 1 (2026-05-30): 영수증 AI 월 분석 횟수를 티어별로 게이팅한다 (실결제는 유예).
 * 한도는 제품 정책 상수 — 향후 PG 정기결제 도입 시 가격/티어와 매핑.
 *
 * <ul>
 *     <li>{@link #FREE}   기본(무료) 티어 — 영수증 AI 월 10 회</li>
 *     <li>{@link #FAMILY} 친구·가족 가구 — 영수증 AI 월 100 회</li>
 *     <li>{@link #PRO}    유료 티어 — 영수증 AI 무제한</li>
 * </ul>
 */
public enum PlanType {
    FREE("FREE", 10),
    FAMILY("FAMILY", 100),
    PRO("PRO", Integer.MAX_VALUE);

    /** 무제한을 의미하는 sentinel (PRO). UI 에서 이 값이면 "무제한" 으로 표기. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    private final String displayName;
    private final int monthlyReceiptQuota;

    PlanType(String displayName, int monthlyReceiptQuota) {
        this.displayName = displayName;
        this.monthlyReceiptQuota = monthlyReceiptQuota;
    }

    /** UI 표기용 한글 라벨. */
    public String displayName() {
        return displayName;
    }

    /** 이 티어의 월 영수증 AI 분석 한도. {@link #UNLIMITED} 이면 무제한. */
    public int monthlyReceiptQuota() {
        return monthlyReceiptQuota;
    }

    /** 무제한 티어 여부 (UI 표기 분기용). */
    public boolean isUnlimitedReceipts() {
        return monthlyReceiptQuota == UNLIMITED;
    }
}
