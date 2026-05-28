-- V4__recurring_transactions.sql
--
-- 반복 거래 룰 — 월 단위 (day_of_month) 만 지원. 매일 새벽 5시 스케줄러가 due 룰을 발화하면
-- 일반 transactions row 가 생성된다. 멱등은 last_run_year_month (YYYY-MM) 컬럼으로 보장.

CREATE TABLE recurring_transactions (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    household_id            BIGINT        NOT NULL,
    category_id             BIGINT        NOT NULL,
    amount                  DECIMAL(15,2) NOT NULL,
    merchant                VARCHAR(255)  DEFAULT NULL,
    payment_method          VARCHAR(50)   DEFAULT NULL,
    memo                    VARCHAR(500)  DEFAULT NULL,
    day_of_month            INT           NOT NULL,                -- 1~31. 31 처럼 없는 달은 말일로 클램프 (서비스 로직)
    active                  BOOLEAN       NOT NULL DEFAULT TRUE,
    last_run_year_month     VARCHAR(7)    DEFAULT NULL,            -- YYYY-MM. NULL=한 번도 발화 안 함
    created_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_recurring_household FOREIGN KEY (household_id) REFERENCES households(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_recurring_category FOREIGN KEY (category_id) REFERENCES categories(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_recurring_day_of_month CHECK (day_of_month BETWEEN 1 AND 31),
    INDEX idx_recurring_hid_active (household_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
