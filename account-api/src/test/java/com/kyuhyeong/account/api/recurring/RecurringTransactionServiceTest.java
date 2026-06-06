package com.kyuhyeong.account.api.recurring;

import com.kyuhyeong.account.api.transaction.TransactionDtos.CreateTransactionRequest;
import com.kyuhyeong.account.api.transaction.TransactionService;
import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.RecurringTransaction;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.RecurringTransactionRepository;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RecurringTransactionService} 단위 테스트 — fire 로직 (멱등 / 발화일 클램프 / due 판정)
 * 과 create 시 last_run_year_month 초기화 정책 + 격리/검증 가드.
 */
@ExtendWith(MockitoExtension.class)
class RecurringTransactionServiceTest {

    @Mock RecurringTransactionRepository recurringRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock HouseholdRepository householdRepository;
    @Mock TransactionService transactionService;

    @InjectMocks RecurringTransactionService service;

    private Household household;
    private Category category;

    @BeforeEach
    void setupContext() {
        HouseholdContext.set(1L);
        User owner = User.builder().id(1L).email("owner1@example.com").build();
        household = Household.builder().id(1L).owner(owner).build();
        category = Category.builder()
                .id(10L).name("월세").type(CategoryType.FIXED).build();
    }

    @AfterEach
    void clearContext() {
        HouseholdContext.clear();
    }

    private RecurringTransaction rule(long id, int dayOfMonth, String lastRunYm) {
        RecurringTransaction r = RecurringTransaction.builder()
                .household(household)
                .category(category)
                .amount(new BigDecimal("500000"))
                .merchant("집주인")
                .paymentMethod("이체")
                .memo("월세")
                .dayOfMonth(dayOfMonth)
                .active(true)
                .lastRunYearMonth(lastRunYm)
                .build();
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    // ─── runRule fire 로직 ───────────────────────────────────────

    @Test
    @DisplayName("runRule — today >= dayOfMonth 이고 이번 달 미발화면 거래 생성 + markRun")
    void firesWhenDue() {
        RecurringTransaction r = rule(100L, 25, null);
        when(recurringRepository.findAllByActiveTrue()).thenReturn(List.of(r));

        int fired = service.runDueForCurrentHousehold(LocalDate.of(2026, 5, 25));

        assertThat(fired).isEqualTo(1);
        ArgumentCaptor<CreateTransactionRequest> req = ArgumentCaptor.forClass(CreateTransactionRequest.class);
        verify(transactionService).create(req.capture(), eq(1L));
        assertThat(req.getValue().categoryId()).isEqualTo(10L);
        assertThat(req.getValue().amount()).isEqualByComparingTo("500000");
        assertThat(req.getValue().occurredAt()).isEqualTo(LocalDate.of(2026, 5, 25).atTime(12, 0));
        assertThat(r.getLastRunYearMonth()).isEqualTo("2026-05");
    }

    @Test
    @DisplayName("runRule — 같은 달 이미 발화했으면 멱등 skip (transactionService.create 호출 없음)")
    void skipsWhenAlreadyRunThisMonth() {
        RecurringTransaction r = rule(100L, 25, "2026-05");
        when(recurringRepository.findAllByActiveTrue()).thenReturn(List.of(r));

        int fired = service.runDueForCurrentHousehold(LocalDate.of(2026, 5, 28));

        assertThat(fired).isZero();
        verify(transactionService, never()).create(any(), any());
    }

    @Test
    @DisplayName("runRule — 발화일 아직 안 됐으면 skip (today < fireDate)")
    void skipsWhenNotDueYet() {
        RecurringTransaction r = rule(100L, 25, null);
        when(recurringRepository.findAllByActiveTrue()).thenReturn(List.of(r));

        int fired = service.runDueForCurrentHousehold(LocalDate.of(2026, 5, 20));

        assertThat(fired).isZero();
        verify(transactionService, never()).create(any(), any());
    }

    @Test
    @DisplayName("runRule — dayOfMonth=31 이고 짧은 달이면 말일로 클램프 (2026-04-30 fire)")
    void clampsDayOfMonthToShortMonth() {
        RecurringTransaction r = rule(100L, 31, null);
        when(recurringRepository.findAllByActiveTrue()).thenReturn(List.of(r));

        // 2026-04-30 은 4월 말일. dayOfMonth=31 인 룰의 effectiveDay 는 30 으로 클램프 → 발화.
        int fired = service.runDueForCurrentHousehold(LocalDate.of(2026, 4, 30));

        assertThat(fired).isEqualTo(1);
        ArgumentCaptor<CreateTransactionRequest> req = ArgumentCaptor.forClass(CreateTransactionRequest.class);
        verify(transactionService).create(req.capture(), eq(1L));
        assertThat(req.getValue().occurredAt()).isEqualTo(LocalDate.of(2026, 4, 30).atTime(12, 0));
    }

    // ─── runDueAcrossHouseholds (스케줄러 경로) ──────────────────

    @Test
    @DisplayName("runDueAcrossHouseholds — 한 가구 실패해도 다음 가구 계속 + failedHouseholds 카운트")
    void acrossCountsFailuresAndContinues() {
        User owner2 = User.builder().id(2L).build();
        Household household2 = Household.builder().id(2L).owner(owner2).build();
        when(householdRepository.findAll()).thenReturn(List.of(household, household2));
        // 가구 1 은 조회 단계에서 실패, 가구 2 는 due 룰 1개 발화 — 컨텍스트로 분기
        RecurringTransaction r2 = RecurringTransaction.builder()
                .household(household2).category(category)
                .amount(new BigDecimal("10000")).dayOfMonth(1).active(true)
                .build();
        ReflectionTestUtils.setField(r2, "id", 200L);
        when(recurringRepository.findAllByActiveTrue()).thenAnswer(inv -> {
            if (HouseholdContext.get() == 1L) {
                throw new RuntimeException("boom");
            }
            return List.of(r2);
        });

        RecurringTransactionService.AcrossResult result =
                service.runDueAcrossHouseholds(LocalDate.of(2026, 5, 25));

        assertThat(result.fired()).isEqualTo(1);
        assertThat(result.failedHouseholds()).isEqualTo(1);
        verify(transactionService).create(any(), eq(2L));
    }

    @Test
    @DisplayName("runDueAcrossHouseholds — 전 가구 정상이면 failedHouseholds=0")
    void acrossAllOk() {
        when(householdRepository.findAll()).thenReturn(List.of(household));
        RecurringTransaction r = rule(100L, 25, null);
        when(recurringRepository.findAllByActiveTrue()).thenReturn(List.of(r));

        RecurringTransactionService.AcrossResult result =
                service.runDueAcrossHouseholds(LocalDate.of(2026, 5, 25));

        assertThat(result.fired()).isEqualTo(1);
        assertThat(result.failedHouseholds()).isZero();
    }

    // ─── create 시 last_run 초기화 ─────────────────────────────

    @Test
    @DisplayName("create — today.day < dayOfMonth 면 lastRunYearMonth=null (이번 달에 발화 예정)")
    void createBeforeFireDayLeavesNullLastRun() {
        when(householdRepository.getReferenceById(1L)).thenReturn(household);
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(recurringRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecurringTransaction created = service.create(10L, new BigDecimal("500000"),
                "x", "y", "z", 25, true, LocalDate.of(2026, 5, 10));

        assertThat(created.getLastRunYearMonth()).isNull();
    }

    @Test
    @DisplayName("create — today.day >= dayOfMonth 이면 lastRunYearMonth=현재월 (다음 달부터 발화)")
    void createAfterFireDayInitializesLastRunToCurrentMonth() {
        when(householdRepository.getReferenceById(1L)).thenReturn(household);
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(recurringRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecurringTransaction created = service.create(10L, new BigDecimal("500000"),
                "x", "y", "z", 25, true, LocalDate.of(2026, 5, 26));

        assertThat(created.getLastRunYearMonth()).isEqualTo("2026-05");
    }

    // ─── 입력 검증 ─────────────────────────────────────────────

    @Test
    @DisplayName("create — dayOfMonth 가 1..31 범위 밖이면 IllegalArgumentException")
    void createRejectsBadDayOfMonth() {
        assertThatThrownBy(() ->
                service.create(10L, new BigDecimal("1000"), null, null, null, 32, true, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dayOfMonth");
        assertThatThrownBy(() ->
                service.create(10L, new BigDecimal("1000"), null, null, null, 0, true, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create — amount 가 양수 아니면 IllegalArgumentException")
    void createRejectsNonPositiveAmount() {
        assertThatThrownBy(() ->
                service.create(10L, BigDecimal.ZERO, null, null, null, 15, true, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
        assertThatThrownBy(() ->
                service.create(10L, null, null, null, null, 15, true, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
