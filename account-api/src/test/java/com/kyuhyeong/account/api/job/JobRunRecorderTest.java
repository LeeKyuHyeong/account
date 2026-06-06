package com.kyuhyeong.account.api.job;

import com.kyuhyeong.account.core.entity.JobRun;
import com.kyuhyeong.account.core.repository.JobRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * {@link JobRunRecorder} 단위 테스트 — @Scheduled 잡 실행 결과 적재.
 */
@ExtendWith(MockitoExtension.class)
class JobRunRecorderTest {

    @Mock JobRunRepository jobRunRepository;

    @InjectMocks JobRunRecorder recorder;

    @Test
    @DisplayName("record — jobName/ok/detail 로 JobRun 저장")
    void recordSavesRun() {
        recorder.record("recurring-daily", true, "fired=3, failedHouseholds=0");

        ArgumentCaptor<JobRun> captor = ArgumentCaptor.forClass(JobRun.class);
        verify(jobRunRepository).save(captor.capture());
        JobRun saved = captor.getValue();
        assertThat(saved.getJobName()).isEqualTo("recurring-daily");
        assertThat(saved.isOk()).isTrue();
        assertThat(saved.getDetail()).isEqualTo("fired=3, failedHouseholds=0");
    }

    @Test
    @DisplayName("record — detail 255자 초과면 잘라서 저장 (예외 메시지 방어)")
    void recordTruncatesLongDetail() {
        recorder.record("push-digest-daily", false, "E".repeat(300));

        ArgumentCaptor<JobRun> captor = ArgumentCaptor.forClass(JobRun.class);
        verify(jobRunRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).hasSize(255);
    }

    @Test
    @DisplayName("record — detail null 허용")
    void recordAllowsNullDetail() {
        recorder.record("push-closing-monthly", true, null);

        ArgumentCaptor<JobRun> captor = ArgumentCaptor.forClass(JobRun.class);
        verify(jobRunRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
    }
}
