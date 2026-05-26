package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 웹 폼/목록의 카테고리 드롭다운용 조회 — sort_order 정렬.
 *
 * <p>가구 격리는 Hibernate {@code householdFilter} 가 자동 적용. 웹 컨트롤러는 @Transactional
 * 경계가 없으므로(open-in-view=false) 카테고리 조회를 본 서비스의 @Transactional 안에서 한다.
 * CategoryController(REST) 의 조회와 동일 로직이나, M4 에서 REST 제거 시 이쪽으로 일원화한다.
 */
@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<Category> findAllSorted() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .toList();
    }

    /**
     * 카테고리 월 예산 수정. {@code findAll()}(householdFilter 적용) 에서 id 로 찾는다 —
     * {@code findById}(PK 직접 로드)는 필터가 안 걸려 다른 가구 카테고리도 수정 가능한 격리
     * 누수가 있으므로 쓰지 않는다. 가구당 카테고리는 ~수십 개라 in-memory 필터로 충분.
     */
    @Transactional
    public void updateBudget(Long categoryId, BigDecimal budgetMonthly) {
        Category category = categoryRepository.findAll().stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found in current household: " + categoryId));
        category.updateBudget(budgetMonthly);
    }
}
