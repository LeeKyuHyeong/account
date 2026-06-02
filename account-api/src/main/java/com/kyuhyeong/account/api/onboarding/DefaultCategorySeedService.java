package com.kyuhyeong.account.api.onboarding;

import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 신규 가구 기본 카테고리 시더.
 *
 * <p>기존 가구는 Flyway 시드(V2)로 카테고리가 들어갔지만, 온보딩으로 만든 신규 가구는 코드로
 * 기본 세트를 넣어준다. 개인화 항목(아내/ISA 등)은 빼고 일반적인 세트만 — 가구가 이후 자유롭게
 * 추가/수정/삭제한다.
 *
 * <p>{@link HouseholdOnboardingService#createHousehold} 의 트랜잭션 안에서 호출된다
 * (별도 @Transactional 없음). INSERT 는 householdFilter 영향을 받지 않으므로 컨텍스트 없이도 안전.
 */
@Service
@RequiredArgsConstructor
public class DefaultCategorySeedService {

    private final CategoryRepository categoryRepository;

    private record Seed(String name, CategoryType type) {
    }

    /** 기본 카테고리 — 정렬 순서는 리스트 순서. */
    private static final List<Seed> DEFAULTS = List.of(
            new Seed("월급", CategoryType.INCOME),
            new Seed("보너스", CategoryType.INCOME),
            new Seed("기타 수입", CategoryType.INCOME),
            new Seed("주거", CategoryType.FIXED),
            new Seed("통신", CategoryType.FIXED),
            new Seed("보험", CategoryType.FIXED),
            new Seed("구독", CategoryType.FIXED),
            new Seed("식비", CategoryType.VARIABLE),
            new Seed("외식/카페", CategoryType.VARIABLE),
            new Seed("교통", CategoryType.VARIABLE),
            new Seed("문화/여가", CategoryType.VARIABLE),
            new Seed("생활용품", CategoryType.VARIABLE),
            new Seed("의료/건강", CategoryType.VARIABLE),
            new Seed("의류/미용", CategoryType.VARIABLE),
            new Seed("기타 변동", CategoryType.VARIABLE),
            new Seed("비상금", CategoryType.INVEST),
            new Seed("저축", CategoryType.INVEST),
            new Seed("투자", CategoryType.INVEST)
    );

    /** 신규 가구에 기본 카테고리를 일괄 생성한다. */
    public void seed(Household household) {
        int order = 0;
        for (Seed s : DEFAULTS) {
            categoryRepository.save(Category.builder()
                    .household(household)
                    .name(s.name())
                    .type(s.type())
                    .budgetMonthly(BigDecimal.ZERO)
                    .sortOrder(order++)
                    .build());
        }
    }
}
