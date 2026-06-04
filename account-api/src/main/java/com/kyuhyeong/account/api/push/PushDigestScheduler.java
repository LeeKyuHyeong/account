package com.kyuhyeong.account.api.push;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * 일일 푸시 다이제스트 스케줄러 — 매일 KST 21:00.
 *
 * <p>CLAUDE.md 의 "@Scheduled 잡 2개 이상이면 account-batch 이전" 규칙 검토 결과 <b>보류</b>:
 * account-batch 는 account-core 에만 의존하는 정책인데 본 잡은 PushSendService /
 * ReceiptAccuracyService (account-api) 가 필요하다. 반복거래 스케줄러
 * ({@code RecurringTransactionScheduler}) 와 같은 이유로 account-api 에 거치 —
 * 이전하려면 의존성 정책 변경이 선행돼야 한다 (사용자 승인 필요 사안).
 */
@Component
@RequiredArgsConstructor
public class PushDigestScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PushDigestService digestService;

    /** 저녁 9시 — 하루 지출이 대체로 끝나고 잠들기 전 확인 가능한 시각. */
    @Scheduled(cron = "0 0 21 * * *", zone = "Asia/Seoul")
    public void sendDailyDigest() {
        digestService.sendDailyDigestAcrossHouseholds(LocalDate.now(KST));
    }

    /** 매월 1일 아침 9시 — 지난달 결산 요약 ("5월 결산: 수입 · 지출 · 잉여"). */
    @Scheduled(cron = "0 0 9 1 * *", zone = "Asia/Seoul")
    public void sendMonthlyClosing() {
        digestService.sendMonthlyClosingAcrossHouseholds(YearMonth.now(KST).minusMonths(1));
    }
}
