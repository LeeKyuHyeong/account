package com.kyuhyeong.account.api.recurring;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 반복 거래 자동 발화 — 매일 KST 05:00 에 전 가구의 활성 룰 중 due 인 것을 발화.
 *
 * <p>{@code AccountApiApplication} 의 {@code @EnableScheduling} 이 활성화 조건. 단일 프로세스에서
 * 잡이 한 번만 트리거되므로 클러스터 락은 불필요 (현 인프라 = 단일 컨테이너).
 *
 * <p>잡 자체는 1초 안에 끝나므로 cron 표현식 정확도는 분 단위면 충분.
 */
@Component
@RequiredArgsConstructor
public class RecurringTransactionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionScheduler.class);

    private final RecurringTransactionService recurringService;

    /** 매일 05:00:00 KST. cron 필드: 초·분·시·일·월·요일. */
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        log.info("Recurring scheduler tick — today={}", today);
        int fired = recurringService.runDueAcrossHouseholds(today);
        log.info("Recurring scheduler done — fired={} transactions", fired);
    }
}
