package com.kyuhyeong.account.api.push;

import com.kyuhyeong.account.api.job.JobRunRecorder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.function.Supplier;

/**
 * 일일 푸시 다이제스트 스케줄러 — 매일 KST 21:00.
 *
 * <p>CLAUDE.md 의 "@Scheduled 잡 2개 이상이면 account-batch 이전" 규칙 검토 결과 <b>보류</b>:
 * account-batch 는 account-core 에만 의존하는 정책인데 본 잡은 PushSendService /
 * ReceiptAccuracyService (account-api) 가 필요하다. 반복거래 스케줄러
 * ({@code RecurringTransactionScheduler}) 와 같은 이유로 account-api 에 거치 —
 * 이전하려면 의존성 정책 변경이 선행돼야 한다 (사용자 승인 필요 사안).
 *
 * <p>실행 결과는 {@link JobRunRecorder} 로 job_runs 에 적재 (앱 관리자 화면에서 확인).
 */
@Component
@RequiredArgsConstructor
public class PushDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(PushDigestScheduler.class);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    static final String DAILY_JOB_NAME = "push-digest-daily";
    static final String CLOSING_JOB_NAME = "push-closing-monthly";

    private final PushDigestService digestService;
    private final JobRunRecorder jobRunRecorder;

    /** 저녁 9시 — 하루 지출이 대체로 끝나고 잠들기 전 확인 가능한 시각. */
    @Scheduled(cron = "0 0 21 * * *", zone = "Asia/Seoul")
    public void sendDailyDigest() {
        recordRun(DAILY_JOB_NAME,
                () -> digestService.sendDailyDigestAcrossHouseholds(LocalDate.now(KST)));
    }

    /** 매월 1일 아침 9시 — 지난달 결산 요약 ("5월 결산: 수입 · 지출 · 잉여"). */
    @Scheduled(cron = "0 0 9 1 * *", zone = "Asia/Seoul")
    public void sendMonthlyClosing() {
        recordRun(CLOSING_JOB_NAME,
                () -> digestService.sendMonthlyClosingAcrossHouseholds(YearMonth.now(KST).minusMonths(1)));
    }

    private void recordRun(String jobName, Supplier<PushDigestService.AcrossResult> job) {
        try {
            PushDigestService.AcrossResult result = job.get();
            jobRunRecorder.record(jobName, result.failedHouseholds() == 0,
                    "households=" + result.households()
                            + ", failedHouseholds=" + result.failedHouseholds());
        } catch (Exception e) {
            jobRunRecorder.record(jobName, false, e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("Push scheduler job {} failed", jobName, e);
        }
    }
}
