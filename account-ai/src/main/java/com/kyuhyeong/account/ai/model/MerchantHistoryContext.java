package com.kyuhyeong.account.ai.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 프롬프트에 주입할 가맹점 학습 이력 컨텍스트.
 *
 * <p>가구별로 자주 결제한 가맹점들과 그때 분류된 카테고리를 모아 Claude에
 * 사전 정보로 전달한다. 이로써 동일 가맹점의 영수증이 매번 다른 카테고리로
 * 분류되는 것을 방지하고, 가구 고유의 소비 패턴(예: 이 부부는 GS25에서
 * 주로 식료품을 산다)을 반영한다.
 *
 * <p>너무 많은 이력을 주입하면 토큰 비용이 증가하므로 최근 사용순 상위 N개로
 * 제한 (기본 20개). 빈 컨텍스트는 정상 상태(신규 가구)이며 학습 없이 분류.
 */
public record MerchantHistoryContext(
        Long householdId,
        List<Entry> entries
) {

    /** 프롬프트에 주입할 기본 최대 이력 개수. */
    public static final int DEFAULT_MAX_ENTRIES = 20;

    /**
     * 빈 컨텍스트 (학습 이력 없음).
     */
    public static MerchantHistoryContext empty(Long householdId) {
        return new MerchantHistoryContext(householdId, List.of());
    }

    /**
     * 학습 이력 한 줄.
     *
     * @param merchantName 가맹점명 (예: "스타벅스 강남역사거리점")
     * @param categoryName 분류된 카테고리명 (예: "외식/카페")
     * @param count        총 분류 횟수 (신뢰도 추정용)
     * @param lastUsedAt   마지막 사용 시각 (재정렬용)
     */
    public record Entry(
            String merchantName,
            String categoryName,
            int count,
            LocalDateTime lastUsedAt
    ) {}
}
