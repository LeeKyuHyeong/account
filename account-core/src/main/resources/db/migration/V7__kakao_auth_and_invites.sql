-- V7__kakao_auth_and_invites.sql
--
-- 카카오 OAuth2 단독 인증 전환 + 가구 초대코드 (2026-06-02):
--   users — provider / provider_user_id 추가(카카오 식별 키), email / password_hash 를 NULL 허용으로 완화.
--           카카오는 닉네임 scope(profile_nickname)만 사용 → email 미수집(nullable).
--           비번 로그인 제거 → password_hash 미사용(nullable). 둘 다 드롭하지 않고 보존(비파괴).
--           기존 dev 시드 유저(V3)는 provider_user_id NULL 유지 → 첫 카카오 로그인 시 코드가 링크
--           (account.dev.kakao-links 매핑). MariaDB UNIQUE 는 NULL 을 동등 취급 안 하므로 NULL 다중 허용.
--   invite_codes — OWNER 가 가구 설정에서 발급하는 초대코드. 가입 전(가구 컨텍스트 없음)에 조회되므로
--                  @Filter 미적용 (User / Household / HouseholdMember 와 동일 비격리군). 코드로 직접 가드.

ALTER TABLE users
    ADD COLUMN provider         VARCHAR(20)  NOT NULL DEFAULT 'KAKAO' AFTER id,
    ADD COLUMN provider_user_id VARCHAR(64)  DEFAULT NULL            AFTER provider,
    MODIFY COLUMN email         VARCHAR(255) DEFAULT NULL,
    MODIFY COLUMN password_hash VARCHAR(100) DEFAULT NULL,
    ADD CONSTRAINT uk_users_provider UNIQUE (provider, provider_user_id);

CREATE TABLE invite_codes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(16)  NOT NULL,
    household_id    BIGINT       NOT NULL,
    created_by      BIGINT       NOT NULL,
    expires_at      DATETIME(6)  DEFAULT NULL,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    used_count      INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_invite_codes_code UNIQUE (code),
    CONSTRAINT fk_invite_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_invite_created_by FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_invite_household (household_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
