-- V1__init_schema.sql
--
-- account-app 초기 스키마. docs/account.md §6.1 (Multi-tenant ER) 를 그대로 옮긴 것.
-- 모든 도메인 테이블은 household_id 컬럼 + FK 를 가진다 (Task 4 에서 Hibernate Filter
-- 로 자동 격리). FK 기본 정책은 ON DELETE RESTRICT (§8.2 Task 2).
--
-- 대상: MariaDB 11.x, utf8mb4_unicode_ci.

-- ─────────────────────────────────────────────────────────────
-- 가구 / 사용자 (격리의 루트)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    fcm_token       VARCHAR(255) DEFAULT NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_login_at   DATETIME(6)  DEFAULT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE households (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                    VARCHAR(100) NOT NULL,
    plan_type               VARCHAR(20)  NOT NULL DEFAULT 'PERSONAL',  -- PERSONAL | FAMILY | PRO
    owner_user_id           BIGINT       NOT NULL,
    data_retention_months   INT          NOT NULL DEFAULT 60,
    max_members             INT          NOT NULL DEFAULT 20,
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_households_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE household_members (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    household_id    BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',  -- OWNER | MEMBER
    invited_by      BIGINT       DEFAULT NULL,
    joined_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_household_members UNIQUE (household_id, user_id),
    CONSTRAINT fk_hm_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_hm_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_hm_invited_by FOREIGN KEY (invited_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 카테고리 / 예산 (가구별)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE categories (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    household_id    BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(20)  NOT NULL,  -- INCOME | FIXED | VARIABLE | INVEST
    budget_monthly  DECIMAL(15,2) NOT NULL DEFAULT 0,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_categories_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_categories_hid_sort (household_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 영수증 (이미지 + Claude 원본 응답 보관)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE receipts (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    household_id        BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,                   -- 업로더
    image_path          VARCHAR(500) NOT NULL,                   -- /mnt/data/receipts/{hid}/{yyyy}/{mm}/{uuid}.jpg
    original_filename   VARCHAR(255) NOT NULL,
    file_size           BIGINT       NOT NULL,
    ocr_raw_json        LONGTEXT     DEFAULT NULL,               -- Claude 원본 응답
    processed_at        DATETIME(6)  DEFAULT NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_receipts_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_receipts_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_receipts_hid_created (household_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 거래 (가계부 본체) + 변경 이력
-- ─────────────────────────────────────────────────────────────

CREATE TABLE transactions (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    household_id            BIGINT        NOT NULL,
    user_id                 BIGINT        NOT NULL,                  -- 입력자
    category_id             BIGINT        NOT NULL,
    amount                  DECIMAL(15,2) NOT NULL,
    occurred_at             DATETIME(6)   NOT NULL,
    merchant                VARCHAR(200)  DEFAULT NULL,
    payment_method          VARCHAR(50)   DEFAULT NULL,
    memo                    VARCHAR(500)  DEFAULT NULL,
    receipt_id              BIGINT        DEFAULT NULL,
    confidence              DECIMAL(4,3)  DEFAULT NULL,              -- 0.000 ~ 1.000
    status                  VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',  -- DRAFT | CONFIRMED
    created_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_user_id      BIGINT        DEFAULT NULL,
    deleted_at              DATETIME(6)   DEFAULT NULL,              -- soft delete (§7.1)
    CONSTRAINT fk_tx_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tx_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tx_category FOREIGN KEY (category_id) REFERENCES categories(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tx_receipt FOREIGN KEY (receipt_id) REFERENCES receipts(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tx_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_tx_hid_occurred (household_id, occurred_at),
    INDEX idx_tx_hid_cat_occurred (household_id, category_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transaction_history (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id      BIGINT       NOT NULL,
    household_id        BIGINT       NOT NULL,
    changed_by_user_id  BIGINT       NOT NULL,
    changed_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    change_type         VARCHAR(20)  NOT NULL,                 -- CREATE | UPDATE | DELETE
    before_json         LONGTEXT     DEFAULT NULL,
    after_json          LONGTEXT     DEFAULT NULL,
    CONSTRAINT fk_th_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_th_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_th_changed_by FOREIGN KEY (changed_by_user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_th_tx_changed (transaction_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 가맹점 학습 (가구별) + 월간 집계
-- ─────────────────────────────────────────────────────────────

CREATE TABLE merchant_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    household_id    BIGINT       NOT NULL,
    merchant_name   VARCHAR(200) NOT NULL,
    category_id     BIGINT       NOT NULL,
    count           INT          NOT NULL DEFAULT 1,
    last_used_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mh_household_merchant UNIQUE (household_id, merchant_name),
    CONSTRAINT fk_mh_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_mh_category FOREIGN KEY (category_id) REFERENCES categories(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE monthly_summaries (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    household_id        BIGINT        NOT NULL,
    `year_month`        CHAR(7)       NOT NULL,             -- 'YYYY-MM' — MariaDB 예약어라 백틱 인용. Task 3 entity 에서 @Column(name = "`year_month`")
    category_id         BIGINT        NOT NULL,
    total_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,
    transaction_count   INT           NOT NULL DEFAULT 0,
    CONSTRAINT uk_ms UNIQUE (household_id, `year_month`, category_id),
    CONSTRAINT fk_ms_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ms_category FOREIGN KEY (category_id) REFERENCES categories(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 순자산 (v1.1) — 컬럼은 미리 두고 화면은 후행
-- ─────────────────────────────────────────────────────────────

CREATE TABLE assets (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    household_id    BIGINT        NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    type            VARCHAR(50)   NOT NULL,
    balance         DECIMAL(15,2) NOT NULL DEFAULT 0,
    recorded_at     DATE          NOT NULL,                 -- YYYY-MM-01 단위
    CONSTRAINT fk_assets_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_assets_hid_recorded (household_id, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE liabilities (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    household_id    BIGINT        NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    type            VARCHAR(50)   NOT NULL,
    balance         DECIMAL(15,2) NOT NULL DEFAULT 0,
    recorded_at     DATE          NOT NULL,
    CONSTRAINT fk_liabilities_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_liabilities_hid_recorded (household_id, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- 결혼 일시 지출 (v1.1, 해당 가구만 사용)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE wedding_items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    household_id    BIGINT        NOT NULL,
    section         VARCHAR(100)  NOT NULL,                 -- 예: '예식', '신혼여행' 등
    name            VARCHAR(200)  NOT NULL,
    budget          DECIMAL(15,2) NOT NULL DEFAULT 0,
    actual          DECIMAL(15,2) NOT NULL DEFAULT 0,
    parent_support  DECIMAL(15,2) NOT NULL DEFAULT 0,
    memo            VARCHAR(500)  DEFAULT NULL,
    paid_at         DATE          DEFAULT NULL,
    CONSTRAINT fk_wedding_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_wedding_household (household_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
