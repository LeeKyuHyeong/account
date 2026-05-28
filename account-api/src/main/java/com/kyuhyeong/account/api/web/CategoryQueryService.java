package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.RecurringTransactionRepository;
import com.kyuhyeong.account.core.repository.TransactionRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 카테고리 조회 + CRUD — 웹 폼 드롭다운, 예산 페이지, 카테고리 관리 페이지가 모두 본 서비스를 쓴다.
 *
 * <p>가구 격리는 Hibernate {@code householdFilter} 가 자동 적용된다. 웹 컨트롤러는 트랜잭션
 * 경계가 없으므로(open-in-view=false) 모든 작업은 본 서비스의 {@code @Transactional} 안에서 한다.
 *
 * <p>단건 조회는 {@code findAll().filter()} 로 — {@code findById}(PK 직접 로드)는 Hibernate
 * filter 가 안 걸려 다른 가구 카테고리도 로드되는 격리 누수가 있다. 가구당 카테고리는 ~수십
 * 개라 in-memory 필터로 충분.
 */
@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;
    private final HouseholdRepository householdRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringRepository;

    @Transactional(readOnly = true)
    public List<Category> findAllSorted() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .toList();
    }

    /**
     * 카테고리 월 예산 수정 — 예산 페이지의 인라인 폼 진입점. 전체 편집은 {@link #edit} 가 담당.
     */
    @Transactional
    public void updateBudget(Long categoryId, BigDecimal budgetMonthly) {
        Category category = findOwned(categoryId);
        category.updateBudget(budgetMonthly);
    }

    /** 카테고리 신규 등록 — 카테고리 관리 페이지의 추가 폼 진입점. */
    @Transactional
    public Category create(String name, CategoryType type, BigDecimal budgetMonthly, int sortOrder) {
        Long householdId = HouseholdContext.get();
        Household household = householdRepository.getReferenceById(householdId);
        Category category = Category.builder()
                .household(household)
                .name(name)
                .type(type)
                .budgetMonthly(budgetMonthly == null ? BigDecimal.ZERO : budgetMonthly)
                .sortOrder(sortOrder)
                .build();
        return categoryRepository.save(category);
    }

    /** 카테고리 전체 편집 — 이름·타입·예산·정렬순서 한 번에. */
    @Transactional
    public void edit(Long categoryId, String name, CategoryType type,
                     BigDecimal budgetMonthly, int sortOrder) {
        Category category = findOwned(categoryId);
        category.edit(name, type,
                budgetMonthly == null ? BigDecimal.ZERO : budgetMonthly, sortOrder);
    }

    /**
     * 카테고리 삭제 — 해당 카테고리를 참조하는 거래 (soft-delete 포함) 또는 반복 거래 룰 (활성/비활성
     * 무관) 이 1건이라도 있으면 거부. DB FK 가 ON DELETE RESTRICT 라 어차피 막히지만, 사전 카운트로
     * 사용자에게 친절한 메시지 + 정확히 어디서 막혔는지 (거래 vs 반복) 안내.
     */
    @Transactional
    public void delete(Long categoryId) {
        Category category = findOwned(categoryId);
        long txInUse = transactionRepository.countByCategoryId(categoryId);
        long ruleInUse = recurringRepository.countByCategoryId(categoryId);
        if (txInUse > 0 || ruleInUse > 0) {
            StringBuilder msg = new StringBuilder("이 카테고리는 ");
            if (txInUse > 0) {
                msg.append("거래 ").append(txInUse).append("건");
            }
            if (txInUse > 0 && ruleInUse > 0) {
                msg.append(" / ");
            }
            if (ruleInUse > 0) {
                msg.append("반복 거래 ").append(ruleInUse).append("건");
            }
            msg.append("에서 사용 중입니다. 먼저 다른 카테고리로 옮기거나 삭제하세요.");
            throw new IllegalStateException(msg.toString());
        }
        categoryRepository.delete(category);
    }

    /**
     * id 단건 조회 — householdFilter 가 적용되는 {@code findAll()} 위에서 in-memory 매칭.
     * {@code findById}(PK 직접 로드)는 필터가 안 걸려 다른 가구 카테고리도 노출되는 격리 누수가
     * 있으므로 쓰지 않는다.
     */
    private Category findOwned(Long categoryId) {
        return categoryRepository.findAll().stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found in current household: " + categoryId));
    }
}
