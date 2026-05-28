package com.kyuhyeong.account.api.transaction;

import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.Transaction;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.enums.TransactionStatus;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.TransactionRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TransactionService} 단위 테스트 — softDelete 중심.
 *
 * <p>가구 격리 자체는 Hibernate {@code householdFilter} 가 책임 — 본 테스트는 서비스의
 * (1) findOne(Specification) 격리 가드 진입 (2) soft-delete 비즈니스 메서드 호출
 * (3) 변경 이력 적재만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock UserRepository userRepository;
    @Mock HouseholdRepository householdRepository;
    @Mock TransactionHistoryService historyService;
    @Mock MerchantHistoryService merchantHistoryService;

    @InjectMocks TransactionService service;

    @BeforeEach
    void bindHousehold() {
        HouseholdContext.set(1L);
    }

    @AfterEach
    void clearHousehold() {
        HouseholdContext.clear();
    }

    private static Transaction newConfirmedTx() {
        Household household = Household.builder().id(1L).build();
        Category category = Category.builder()
                .id(10L).name("식비").type(CategoryType.VARIABLE).build();
        User author = User.builder().id(1L).email("owner1@example.com").build();
        Transaction tx = Transaction.builder()
                .household(household)
                .author(author)
                .category(category)
                .amount(new BigDecimal("12345"))
                .occurredAt(LocalDateTime.now())
                .merchant("스타벅스")
                .paymentMethod("CARD")
                .memo(null)
                .status(TransactionStatus.CONFIRMED)
                .build();
        ReflectionTestUtils.setField(tx, "id", 100L);
        return tx;
    }

    @Test
    @DisplayName("softDelete — findOne 으로 격리 안전 로드 후 tx.softDelete + logDelete 호출")
    void softDeleteHappyPath() {
        Transaction tx = newConfirmedTx();
        when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.of(tx));
        User actor = User.builder().id(1L).build();
        when(userRepository.getReferenceById(1L)).thenReturn(actor);

        service.softDelete(100L, 1L);

        assertThat(tx.getDeletedAt()).isNotNull();
        verify(historyService).logDelete(eq(tx), any(), eq(1L));
    }

    @Test
    @DisplayName("softDelete — 다른 가구 또는 미존재 거래면 IllegalArgumentException + 이력 적재 없음")
    void softDeleteRejectsMissingOrCrossHousehold() {
        when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDelete(404L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");

        verify(userRepository, never()).getReferenceById(any());
        verify(historyService, never()).logDelete(any(), any(), any());
    }

    @Test
    @DisplayName("softDelete — 이미 삭제된 거래면 IllegalArgumentException + 이력 적재 없음")
    void softDeleteRejectsAlreadyDeleted() {
        Transaction tx = newConfirmedTx();
        tx.softDelete(User.builder().id(1L).build());
        when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> service.softDelete(100L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already deleted");

        verify(historyService, never()).logDelete(any(), any(), any());
    }
}
