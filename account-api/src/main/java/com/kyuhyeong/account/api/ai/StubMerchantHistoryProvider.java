package com.kyuhyeong.account.api.ai;

import com.kyuhyeong.account.ai.model.MerchantHistoryContext;
import com.kyuhyeong.account.ai.service.MerchantHistoryProvider;
import org.springframework.stereotype.Component;

/**
 * Task 1 placeholder — Spring 컨텍스트 기동 검증용.
 *
 * <p>Task 6 에서 {@code account-core} 모듈의 {@code JpaMerchantHistoryProvider} 가
 * 추가되는 시점에 본 클래스는 <b>완전히 삭제</b>한다. (§10 단순성 원칙)
 */
@Component
public class StubMerchantHistoryProvider implements MerchantHistoryProvider {

    @Override
    public MerchantHistoryContext getRecentHistory(Long householdId, int maxEntries) {
        return MerchantHistoryContext.empty(householdId);
    }
}
