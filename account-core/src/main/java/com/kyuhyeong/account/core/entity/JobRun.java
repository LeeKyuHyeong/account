package com.kyuhyeong.account.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * {@code @Scheduled} 잡 실행 결과 (V9) — 반복거래 / 푸시 다이제스트 / 월말 결산.
 *
 * <p><b>비격리 엔티티</b> ({@code @Filter} 미적용) — 시스템 전역 기록 (user/household 무관).
 * 잡들이 가구 단위 try-catch 로 조용히 실패할 수 있어, 서버 로그 없이도 앱 관리자
 * 화면(/web/sysadmin)에서 "어젯밤 잡이 돌았고 몇 가구가 실패했는지" 확인하는 용도.
 */
@Entity
@Table(name = "job_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** recurring-daily / push-digest-daily / push-closing-monthly */
    @Column(name = "job_name", nullable = false, length = 50)
    private String jobName;

    /** 전체 throw 없음 + 실패 가구 0 일 때만 true. */
    @Column(name = "ok", nullable = false)
    private boolean ok;

    /** "fired=3, failedHouseholds=0" 또는 예외 요약 (255 truncate). */
    @Column(name = "detail", length = 255)
    private String detail;

    @CreationTimestamp
    @Column(name = "ran_at", nullable = false, updatable = false)
    private LocalDateTime ranAt;
}
