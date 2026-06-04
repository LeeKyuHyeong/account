package com.kyuhyeong.account.api.push;

import com.kyuhyeong.account.api.receipt.ReceiptAccuracyService;
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

import java.time.LocalDate;
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
    private final TransactionRepository transactionRepository;
    private final PushSendService pushSendService;

    /** 스케줄러용 — 전 가구 순회. 푸시 비활성(VAPID 미설정)이면 즉시 반환. */
    public void sendDailyDigestAcrossHouseholds(LocalDate today) {
        if (!pushSendService.isEnabled()) {
            return;
        }
        for (Household household : householdRepository.findAll()) {
            try {
                sendDailyDigestForHousehold(household.getId(), today);
            } catch (Exception e) {
                log.warn("Failed daily push digest for household {}", household.getId(), e);
            }
        }
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
