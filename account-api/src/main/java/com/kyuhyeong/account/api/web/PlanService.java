package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.enums.PlanType;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * 구독 플랜 화면용 얇은 어댑터 (구독 Phase 1).
 *
 * <p>가구 티어 조회/변경 + 이번 달 영수증 AI 사용량 집계. 결제 연동은 비범위 — 티어 변경은
 * 즉시 반영된다. 가구는 비격리 엔티티지만 principal 의 신뢰된 활성 가구 ID 만 사용하므로 누수 없음.
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HouseholdRepository householdRepository;
    private final ReceiptRepository receiptRepository;

    /** 현재 티어 + 이번 달 사용량/한도 + 선택 가능한 전체 티어 목록. */
    @Transactional(readOnly = true)
    public PlanView view(Long householdId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new IllegalStateException("Household not found: " + householdId));
        PlanType current = household.getPlanType();
        long used = receiptRepository.countByCreatedAtGreaterThanEqual(monthStart());

        List<PlanOption> options = Arrays.stream(PlanType.values())
                .map(t -> new PlanOption(
                        t.name(),
                        t.displayName(),
                        t.monthlyReceiptQuota(),
                        t.isUnlimitedReceipts(),
                        t == current))
                .toList();

        return new PlanView(
                current.name(),
                current.displayName(),
                used,
                current.monthlyReceiptQuota(),
                current.isUnlimitedReceipts(),
                options);
    }

    /** 가구 티어 변경 (즉시 반영). */
    @Transactional
    public void changePlan(Long householdId, PlanType newPlan) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new IllegalStateException("Household not found: " + householdId));
        household.changePlan(newPlan);
        householdRepository.save(household);
    }

    private LocalDateTime monthStart() {
        return LocalDate.now(KST).withDayOfMonth(1).atStartOfDay();
    }

    /** 플랜 화면 전체 뷰모델. */
    public record PlanView(
            String currentType,
            String currentDisplayName,
            long usedThisMonth,
            int currentQuota,
            boolean currentUnlimited,
            List<PlanOption> options) {}

    /** 티어 카드 1개. */
    public record PlanOption(
            String type,
            String displayName,
            int quota,
            boolean unlimited,
            boolean current) {}
}
