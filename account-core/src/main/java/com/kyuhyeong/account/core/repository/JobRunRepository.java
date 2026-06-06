package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 잡 실행 이력 Repository — 비격리 (조회는 앱 관리자 화면 전용).
 */
public interface JobRunRepository extends JpaRepository<JobRun, Long> {

    /** 최근 실행 15건 — 앱 관리자 화면 잡 실행 현황 섹션 (일 3행 수준이라 ~5일치). */
    List<JobRun> findTop15ByOrderByIdDesc();
}
