package com.kyuhyeong.account.api.push;

import com.kyuhyeong.account.api.receipt.ReceiptAccuracyService;
import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.MonthlySummaryResponse;
import com.kyuhyeong.account.api.summary.MonthlySummaryService;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.enums.TransactionStatus;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.TransactionRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 일일 푸시 다이제스트 (푸시 알림 2단계).
 *
 * <ul>
 *   <li>① 오늘의 영수증 분석 요약 — 오늘 업로드 N건 / 수정 M건 ({@link ReceiptAccuracyService}
 *       의 비교 행 재사용 — "자동 요약" 논의의 푸시 채널 완성)</li>
 *   <li>② DRAFT 미확정 리마인더 — 확정 안 한 거래 N건 (있을 때만)</li>
 * </ul>
 *
 * <p>가구 순회/컨텍스트 패턴은 {@code RecurringTransactionService} 와 동일 — 가구별 자체
 * {@code @Transactional} + {@code HouseholdContext} 명시 set/clear, 가구 단위 try-catch 로
 * 한 가구 실패가 다음 가구를 막지 않는다. 보낼 내용이 없는 가구(오늘 분석 0건 + DRAFT 0건)는
 * 발송하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PushDigestService {

    private static final Logger log = LoggerFactory.getLogger(PushDigestService.class);

    /** 분석 요약 집계 윈도 — 분석 이력 화면과 동일한 "최근 N건" 기준. */
    private static final int ACCURACY_WINDOW_ROWS = 30;

    private final HouseholdRepository householdRepository;
    private final ReceiptAccuracyService receiptAccuracyService;
    private final MonthlySummaryService monthlySummaryService;
    private final TransactionRepository transactionRepository;
    private final PushSendService pushSendService;

    /** 스케줄러용 — 전 가구 순회. 푸시 비활성(VAPID 미설정)이면 즉시 (0, 0) 반환. */
    public AcrossResult sendDailyDigestAcrossHouseholds(LocalDate today) {
        if (!pushSendService.isEnabled()) {
            return new AcrossResult(0, 0);
        }
        int households = 0;
        int failed = 0;
        for (Household household : householdRepository.findAll()) {
            try {
                sendDailyDigestForHousehold(household.getId(), today);
                households++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed daily push digest for household {}", household.getId(), e);
            }
        }
        return new AcrossResult(households, failed);
    }

    /** 단일 가구 다이제스트 — 컨텍스트 명시 설정/해제 (web filter 가 없는 스케줄러 경로). */
    @Transactional
    public void sendDailyDigestForHousehold(Long householdId, LocalDate today) {
        HouseholdContext.set(householdId);
        try {
            digest(householdId, today);
        } finally {
            HouseholdContext.clear();
        }
    }

    /** 웹 수동 실행용 — {@code SessionHouseholdContextFilter} 가 컨텍스트를 이미 채운 상태. */
    @Transactional
    public void sendDailyDigestForCurrentHousehold(LocalDate today) {
        digest(HouseholdContext.get(), today);
    }

    // ─── 월간 결산 ────────────────────────────────────────────────

    /** 스케줄러용 — 전 가구에 지난달 결산 푸시. 푸시 비활성이면 즉시 (0, 0) 반환. */
    public AcrossResult sendMonthlyClosingAcrossHouseholds(YearMonth month) {
        if (!pushSendService.isEnabled()) {
            return new AcrossResult(0, 0);
        }
        int households = 0;
        int failed = 0;
        for (Household household : householdRepository.findAll()) {
            try {
                sendMonthlyClosingForHousehold(household.getId(), month);
                households++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed monthly closing push for household {}", household.getId(), e);
            }
        }
        return new AcrossResult(households, failed);
    }

    /** 전 가구 순회 결과 — 처리 가구 수 + 실패 가구 수 (잡 실행 이력용). */
    public record AcrossResult(int households, int failedHouseholds) {}

    /** 단일 가구 월 결산 — 컨텍스트 명시 설정/해제 (스케줄러 경로). */
    @Transactional
    public void sendMonthlyClosingForHousehold(Long householdId, YearMonth month) {
        HouseholdContext.set(householdId);
        try {
            monthlyClosing(householdId, month);
        } finally {
            HouseholdContext.clear();
        }
    }

    /** 웹 수동 실행용 — 컨텍스트는 web filter 가 이미 채운 상태. */
    @Transactional
    public void sendMonthlyClosingForCurrentHousehold(YearMonth month) {
        monthlyClosing(HouseholdContext.get(), month);
    }

    /** "5월 결산: 수입 X · 지출 Y · 잉여 ±Z" — 그 달 거래가 전혀 없는 가구는 무발송. */
    private void monthlyClosing(Long householdId, YearMonth month) {
        MonthlySummaryResponse summary = monthlySummaryService.get(month);
        if (summary.income().signum() == 0 && summary.totalExpense().signum() == 0) {
            return;
        }
        String body = "수입 " + won(summary.income())
                + " · 지출 " + won(summary.totalExpense())
                + " · 잉여 " + (summary.surplus().signum() >= 0 ? "+" : "") + won(summary.surplus());
        String url = "/web/report?from=" + month.atDay(1) + "&to=" + month.atEndOfMonth();
        pushSendService.sendToHouseholdExcept(householdId, null,
                month.getMonthValue() + "월 결산", body, url);
    }

    private static String won(BigDecimal amount) {
        return String.format("%,d원", amount.longValue());
    }

    private void digest(Long householdId, LocalDate today) {
        // ① 오늘의 영수증 분석 요약 (오늘 업로드분이 있을 때만)
        List<ReceiptAccuracyService.Row> todayRows =
                receiptAccuracyService.listRecent(ACCURACY_WINDOW_ROWS).stream()
                        .filter(r -> r.uploadedAt().toLocalDate().equals(today))
                        .toList();
        if (!todayRows.isEmpty()) {
            long changed = todayRows.stream().filter(ReceiptAccuracyService.Row::hasDiff).count();
            pushSendService.sendToHouseholdExcept(householdId, null,
                    "오늘의 영수증 분석",
                    todayRows.size() + "건 분석 · " + changed + "건 수정됨",
                    "/web/receipts/analysis");
        }

        // ② DRAFT 미확정 리마인더 (있을 때만)
        long drafts = transactionRepository.countByStatusAndDeletedAtIsNull(TransactionStatus.DRAFT);
        if (drafts > 0) {
            pushSendService.sendToHouseholdExcept(householdId, null,
                    "확정 대기 거래",
                    "확정하지 않은 거래 " + drafts + "건이 있어요",
                    "/web/transactions?status=DRAFT");
        }
    }
}
