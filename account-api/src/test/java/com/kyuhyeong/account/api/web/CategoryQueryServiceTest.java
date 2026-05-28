package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.RecurringTransactionRepository;
import com.kyuhyeong.account.core.repository.TransactionRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CategoryQueryService} 단위 테스트 — 카테고리 관리 페이지가 호출하는 create / edit / delete
 * 동작 + 격리 가드 + 삭제 안전 가드 검증. (예산 수정 / 목록 조회는 기존 동작이라 별도 테스트 생략 —
 * 통합 검증은 수동 e2e.)
 */
@ExtendWith(MockitoExtension.class)
class CategoryQueryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock HouseholdRepository householdRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock RecurringTransactionRepository recurringRepository;

    @InjectMocks CategoryQueryService service;

    @BeforeEach
    void bindHousehold() {
        HouseholdContext.set(1L);
    }

    @AfterEach
    void clearHousehold() {
        HouseholdContext.clear();
    }

    private static Category category(long id, String name, CategoryType type) {
        Category c = Category.builder()
                .name(name).type(type).budgetMonthly(BigDecimal.ZERO).sortOrder(10)
                .build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    @Test
    @DisplayName("create — 활성 가구 reference + 입력값으로 빌드 후 save")
    void createBuildsAndSaves() {
        Household household = Household.builder().id(1L).build();
        when(householdRepository.getReferenceById(1L)).thenReturn(household);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = service.create("간식", CategoryType.VARIABLE, new BigDecimal("50000"), 30);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        Category saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("간식");
        assertThat(saved.getType()).isEqualTo(CategoryType.VARIABLE);
        assertThat(saved.getBudgetMonthly()).isEqualByComparingTo("50000");
        assertThat(saved.getSortOrder()).isEqualTo(30);
        assertThat(saved.getHousehold()).isSameAs(household);
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("create — budgetMonthly=null 이면 0 으로 정규화")
    void createDefaultsNullBudgetToZero() {
        when(householdRepository.getReferenceById(1L)).thenReturn(Household.builder().id(1L).build());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("간식", CategoryType.VARIABLE, null, 30);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getBudgetMonthly()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("edit — findAll().filter() 격리 가드 통과 후 Category.edit 호출")
    void editAppliesAllFields() {
        Category target = category(10L, "옛이름", CategoryType.FIXED);
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(5L, "다른카테고리", CategoryType.INCOME), target));

        service.edit(10L, "새이름", CategoryType.VARIABLE, new BigDecimal("100000"), 50);

        assertThat(target.getName()).isEqualTo("새이름");
        assertThat(target.getType()).isEqualTo(CategoryType.VARIABLE);
        assertThat(target.getBudgetMonthly()).isEqualByComparingTo("100000");
        assertThat(target.getSortOrder()).isEqualTo(50);
    }

    @Test
    @DisplayName("edit — 타 가구/미존재 id 면 IllegalArgumentException (findAll 결과에 없음)")
    void editRejectsMissing() {
        when(categoryRepository.findAll()).thenReturn(List.of(category(5L, "X", CategoryType.INCOME)));

        assertThatThrownBy(() ->
                service.edit(999L, "Y", CategoryType.VARIABLE, BigDecimal.ZERO, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("delete — 사용 거래 0건이면 repository.delete 호출")
    void deleteWhenUnused() {
        Category target = category(10L, "예전카테고리", CategoryType.VARIABLE);
        when(categoryRepository.findAll()).thenReturn(List.of(target));
        when(transactionRepository.countByCategoryId(10L)).thenReturn(0L);

        service.delete(10L);

        verify(categoryRepository).delete(target);
    }

    @Test
    @DisplayName("delete — 사용 거래 ≥1건이면 IllegalStateException + 삭제 안 함 (사용자 친절 메시지 포함)")
    void deleteRejectsInUse() {
        Category target = category(10L, "사용중", CategoryType.VARIABLE);
        when(categoryRepository.findAll()).thenReturn(List.of(target));
        when(transactionRepository.countByCategoryId(10L)).thenReturn(7L);

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("7건");

        verify(categoryRepository, never()).delete(any(Category.class));
    }
}
