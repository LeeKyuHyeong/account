package com.kyuhyeong.account.ai.service;

import com.kyuhyeong.account.ai.model.MerchantHistoryContext;

/**
 * 가구별 가맹점 학습 이력을 제공하는 인터페이스.
 *
 * <p>{@code account-ai} 모듈은 학습 이력의 저장 위치(DB / Redis / 메모리 캐시)에
 * 관심 없고, 오직 "최근 N개의 가맹점 분류 이력"이 필요할 뿐이다. 따라서 인터페이스로
 * 분리하고, 구현체는 {@code account-core} 모듈에서 JPA Repository를 활용해
 * {@code merchant_history} 테이블을 조회하도록 한다.
 *
 * <p>이 분리 덕분에 {@code account-ai} 모듈은 {@code account-core}에 컴파일 의존성을
 * 갖지 않으며, 단위 테스트 시에는 in-memory 구현(또는 빈 컨텍스트 반환)으로 쉽게 대체 가능.
 */
public interface MerchantHistoryProvider {

    /**
     * 가구의 최근 분류 이력을 빈도 + 최근성 순으로 정렬하여 반환.
     *
     * @param householdId 가구 ID (활성 가구, JWT 클레임에서 추출됨)
     * @param maxEntries  최대 반환 개수 (프롬프트 토큰 비용 통제)
     * @return 학습 이력 컨텍스트. 이력이 없으면 빈 컨텍스트
     *         ({@link MerchantHistoryContext#empty(Long)}) 반환.
     */
    MerchantHistoryContext getRecentHistory(Long householdId, int maxEntries);
}
