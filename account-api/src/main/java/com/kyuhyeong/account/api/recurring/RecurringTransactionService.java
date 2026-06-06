package com.kyuhyeong.account.api.recurring;

import com.kyuhyeong.account.api.transaction.TransactionDtos.CreateTransactionRequest;
import com.kyuhyeong.account.api.transaction.TransactionService;
import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.RecurringTransaction;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.RecurringTransactionRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * 반복 거래 룰 관리 + 발화 (CRUD + scheduled / manual run).
 *
 * <p>매일 새벽 KST 5시에 {@code RecurringTransactionScheduler} 가 {@link #runDueAcrossHouseholds} 를
 * 호출 — 전 가구의 활성 룰 중 (1) 그 달 미발화 (2) 오늘 ≥ 발화일 인 것을 일반 거래로 적재.
 * 같은 달 재실행 시 멱등 ({@link RecurringTransaction#getLastRunYearMonth}).
 *
 * <p>가구 격리는 Hibernate {@code householdFilter}. 단건 조회는 {@code findAll().filter()} —
 * {@code findById} 는 필터가 안 걸려 격리 누수 발생. 활성 룰 조회는 derived
 * {@code findAllByActiveTrue} 로 — 이 또한 filter 적용 대상.
 */
@Service
@RequiredArgsConstructor
public class RecurringTransactionService {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionService.class);

    private final RecurringTransactionRepository recurringRepository;
    private final CategoryRepository categoryRepository;
    private final HouseholdRepository householdRepository;
    private final TransactionService transactionService;

    // ─── 조회 / CRUD ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RecurringTransaction> findAllInCurrentHousehold() {
        return recurringRepository.findAll().stream()
                .sorted(Comparator.comparingInt(RecurringTransaction::getDayOfMonth)
                        .thenComparing(RecurringTransaction::getId))
                .toList();
    }

    /**
     * 룰 생성. 생성 시점 {@code today.day >= 발화일} 이면 {@code last_run_year_month} 를
     * 현재월로 초기화 → 다음 달부터 발화 (당월 이미 지난 발화일에 대한 의외성 회피).
     */
    @Transactional
    public RecurringTransaction create(Long categoryId, BigDecimal amount, String merchant,
                                       String paymentMethod, String memo, int dayOfMonth,
                                       boolean active, LocalDate today) {
        validateDayOfMonth(dayOfMonth);
        validateAmount(amount);

        Long householdId = HouseholdContext.get();
        Household household = householdRepository.getReferenceById(householdId);
        Category category = findOwnedCategory(categoryId);

        YearMonth currentYm = YearMonth.from(today);
        int effectiveDay = Math.min(dayOfMonth, currentYm.lengthOfMonth());
        String initialLastRun = today.getDayOfMonth() >= effectiveDay ? currentYm.toString() : null;

        RecurringTransaction rule = RecurringTransaction.builder()
                .household(household)
                .category(category)
                .amount(amount)
                .merchant(merchant)
                .paymentMethod(paymentMethod)
                .memo(memo)
                .dayOfMonth(dayOfMonth)
                .active(active)
                .lastRunYearMonth(initialLastRun)
                .build();
        return recurringRepository.save(rule);
    }

    @Transactional
    public void edit(Long id, Long categoryId, BigDecimal amount, String merchant,
                     String paymentMethod, String memo, int dayOfMonth, boolean active) {
        validateDayOfMonth(dayOfMonth);
        validateAmount(amount);
        RecurringTransaction rule = findOwned(id);
        Category category = findOwnedCategory(categoryId);
        rule.edit(category, amount, merchant, paymentMethod, memo, dayOfMonth, active);
    }

    @Transactional
    public void delete(Long id) {
        RecurringTransaction rule = findOwned(id);
        recurringRepository.delete(rule);
    }

    // ─── 발화 ────────────────────────────────────────────────────

    /** "지금 실행" UI 버튼. {@code SessionHouseholdContextFilter} 가 컨텍스트를 이미 채워둔 상태. */
    @Transactional
    public int runDueForCurrentHousehold(LocalDate today) {
        int fired = 0;
        for (RecurringTransaction rule : recurringRepository.findAllByActiveTrue()) {
            if (runRule(rule, today)) fired++;
        }
        return fired;
    }

    /**
     * 스케줄러용 — 전 가구 순회. 각 가구의 트랜잭션은 독립 ({@link #runDueForHousehold} 의 자체
     * {@code @Transactional}) 이라 한 가구 실패가 다음 가구를 막지 않는다. 실패 가구 수는
     * 잡 실행 이력(job_runs)에 남기기 위해 결과로 반환한다.
     */
    public AcrossResult runDueAcrossHouseholds(LocalDate today) {
        int total = 0;
        int failed = 0;
        for (Household household : householdRepository.findAll()) {
            try {
                total += runDueForHousehold(household.getId(), today);
            } catch (Exception e) {
                failed++;
                log.warn("Failed recurring run for household {}", household.getId(), e);
            }
        }
        return new AcrossResult(total, failed);
    }

    /** 전 가구 순회 결과 — 발화 거래 수 + 실패 가구 수. */
    public record AcrossResult(int fired, int failedHouseholds) {}

    /**
     * 단일 가구 발화. 컨텍스트를 명시 설정/해제 — scheduler 처럼 web filter 가 없는 경로에서 호출
     * 됨. self-call 회피를 위해 public 으로 노출 (proxy 통과 + 외부에서도 활용 가능).
     */
    @Transactional
    public int runDueForHousehold(Long householdId, LocalDate today) {
        HouseholdContext.set(householdId);
        try {
            int fired = 0;
            for (RecurringTransaction rule : recurringRepository.findAllByActiveTrue()) {
                if (runRule(rule, today)) fired++;
            }
            return fired;
        } finally {
            HouseholdContext.clear();
        }
    }

    /** 한 룰 한 번 발화 — 같은 달 재실행 멱등 + 발화일 미도래 시 skip. */
    private boolean runRule(RecurringTransaction rule, LocalDate today) {
        YearMonth currentYm = YearMonth.from(today);
        if (currentYm.toString().equals(rule.getLastRunYearMonth())) {
            return false;
        }
        int effectiveDay = Math.min(rule.getDayOfMonth(), currentYm.lengthOfMonth());
        LocalDate fireDate = currentYm.atDay(effectiveDay);
        if (today.isBefore(fireDate)) {
            return false;
        }

        Long authorUserId = rule.getHousehold().getOwner().getId();
        CreateTransactionRequest request = new CreateTransactionRequest(
                rule.getCategory().getId(),
                rule.getAmount(),
                fireDate.atTime(12, 0),
                rule.getMerchant(),
                rule.getPaymentMethod(),
                rule.getMemo()
        );
        transactionService.create(request, authorUserId);
        rule.markRun(currentYm.toString());
        log.info("Recurring rule {} fired for household {} at {}",
                rule.getId(), rule.getHousehold().getId(), fireDate);
        return true;
    }

    // ─── 격리 안전 조회 ─────────────────────────────────────────────

    private RecurringTransaction findOwned(Long id) {
        return recurringRepository.findAll().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Recurring rule not found in current household: " + id));
    }

    private Category findOwnedCategory(Long categoryId) {
        return categoryRepository.findAll().stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found in current household: " + categoryId));
    }

    private static void validateDayOfMonth(int dayOfMonth) {
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new IllegalArgumentException("dayOfMonth must be 1..31: " + dayOfMonth);
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
