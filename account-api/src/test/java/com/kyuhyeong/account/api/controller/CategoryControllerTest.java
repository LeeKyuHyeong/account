package com.kyuhyeong.account.api.controller;

import com.kyuhyeong.account.api.controller.CategoryController.CategoryDto;
import com.kyuhyeong.account.api.controller.CategoryController.UpdateBudgetRequest;
import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link CategoryController#updateBudget} 단위 테스트.
 *
 * <p>가구 격리는 Hibernate {@code householdFilter} 가 책임지므로 본 테스트는 비즈니스
 * 로직 (예산 수정 + 미존재 거부) 만 검증한다. {@code @DecimalMin} validation 은 Spring
 * MVC 가 컨트롤러 진입 전에 처리하므로 단위 테스트 범위 밖.
 */
@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock CategoryRepository categoryRepository;
    @InjectMocks CategoryController controller;

    @Test
    @DisplayName("updateBudget: 예산 갱신 후 갱신된 DTO 반환")
    void updatesBudget() {
        Category category = Category.builder()
                .name("외식")
                .type(CategoryType.VARIABLE)
                .budgetMonthly(new BigDecimal("0"))
                .sortOrder(220)
                .build();
        ReflectionTestUtils.setField(category, "id", 7L);
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));

        CategoryDto dto = controller.updateBudget(
                7L, new UpdateBudgetRequest(new BigDecimal("300000")));

        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.budgetMonthly()).isEqualByComparingTo("300000");
        assertThat(category.getBudgetMonthly()).isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("updateBudget: 미존재 / 다른 가구는 IllegalArgumentException")
    void rejectsMissing() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.updateBudget(
                404L, new UpdateBudgetRequest(new BigDecimal("100000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("404");
    }
}
