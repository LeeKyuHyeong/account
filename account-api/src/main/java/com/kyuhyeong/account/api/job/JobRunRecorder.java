package com.kyuhyeong.account.api.job;

import com.kyuhyeong.account.core.entity.JobRun;
import com.kyuhyeong.account.core.repository.JobRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code @Scheduled} 잡 실행 결과 적재 — 스케줄러들이 실행 직후 호출.
 *
 * <p>잡 이름: {@code recurring-daily} / {@code push-digest-daily} / {@code push-closing-monthly}.
 * 수동 실행 경로(run-now 등)는 기록하지 않는다 (스케줄 발화 가시성이 목적).
 */
@Service
@RequiredArgsConstructor
public class JobRunRecorder {

    private static final int MAX_DETAIL_LENGTH = 255;

    private final JobRunRepository jobRunRepository;

    @Transactional
    public void record(String jobName, boolean ok, String detail) {
        jobRunRepository.save(JobRun.builder()
                .jobName(jobName)
                .ok(ok)
                .detail(truncate(detail))
                .build());
    }

    private static String truncate(String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL_LENGTH) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL_LENGTH);
    }
}
