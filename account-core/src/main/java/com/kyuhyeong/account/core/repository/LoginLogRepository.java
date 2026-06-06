package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 로그인 이력 Repository — 비격리 (조회는 앱 관리자 화면 전용).
 */
public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {

    /** 최근 로그인 30건 — 앱 관리자 화면 접속 로그 섹션. */
    List<LoginLog> findTop30ByOrderByIdDesc();
}
