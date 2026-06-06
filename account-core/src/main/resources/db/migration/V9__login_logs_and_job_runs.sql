-- V9__login_logs_and_job_runs.sql
-- 앱 관리자 화면 2단계 (2026-06-06):
--   login_logs — 카카오 로그인 성공 이력. User 군 비격리 (@Filter 미적용) — 사람 단위 기록이라
--     가구 컨텍스트와 무관하며, 조회는 /web/sysadmin (ROLE_SYSADMIN) 전용.
--     기록 시점은 OnboardingAwareSuccessHandler (로그인 성공 직후). 실패 로그인은 비범위
--     (principal 미존재 — 필요해지면 failure handler 추가).
--   job_runs — @Scheduled 잡 실행 결과 (반복거래/푸시 다이제스트/월말 결산). 시스템 전역
--     (user/household FK 없음). 가구 단위 try-catch 로 조용히 실패하는 잡의 가시성 확보용.
--     볼륨 미미 (일 3행 수준) — 보존기간 정리는 도입하지 않음.

CREATE TABLE login_logs (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    -- IPv6 표기 최대 45자. 운영(nginx 뒤)은 X-Forwarded-For 첫 값, 추출 실패 시 NULL.
    ip         VARCHAR(45)  DEFAULT NULL,
    user_agent VARCHAR(255) DEFAULT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_loginlog_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_loginlog_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_runs (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- recurring-daily / push-digest-daily / push-closing-monthly
    job_name VARCHAR(50)  NOT NULL,
    -- 전체 throw 없음 + 실패 가구 0 일 때만 TRUE
    ok       BOOLEAN      NOT NULL,
    -- "fired=3, failedHouseholds=0" 또는 예외 요약 (255 truncate)
    detail   VARCHAR(255) DEFAULT NULL,
    ran_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
