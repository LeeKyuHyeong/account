-- V2__seed_dev.sql
--
-- 개발 환경 시드.
--   - 가구 2개 (우리집 / 테스트가구) → Task 4 격리 검증의 핵심 토대.
--   - 의도적으로 카테고리 수를 다르게 한다 (우리집 22 vs 테스트가구 5).
--     격리 테스트에서 가구#1 호출이 22개, 가구#2 호출이 5개만 반환되는지로 검증.
--
-- 비밀번호 해시는 placeholder. Task 5 (JWT 인증) 에서 BCrypt 로 재발급한다.
-- 본 시드만으로는 로그인 불가 — 격리 메커니즘 검증에 충분.

-- ─────────────────────────────────────────────────────────────
-- users (4명)
-- ─────────────────────────────────────────────────────────────
INSERT INTO users (id, email, password_hash, name) VALUES
    (1, 'owner1@example.com',  'BCRYPT_PLACEHOLDER_TASK_5', '우리집 오너'),
    (2, 'member1@example.com', 'BCRYPT_PLACEHOLDER_TASK_5', '우리집 멤버'),
    (3, 'owner2@example.com',  'BCRYPT_PLACEHOLDER_TASK_5', '테스트가구 오너'),
    (4, 'member2@example.com', 'BCRYPT_PLACEHOLDER_TASK_5', '테스트가구 멤버');

-- ─────────────────────────────────────────────────────────────
-- households (2개)
-- ─────────────────────────────────────────────────────────────
INSERT INTO households (id, name, plan_type, owner_user_id) VALUES
    (1, '우리집',     'PERSONAL', 1),
    (2, '테스트가구', 'PERSONAL', 3);

-- ─────────────────────────────────────────────────────────────
-- household_members
-- ─────────────────────────────────────────────────────────────
INSERT INTO household_members (household_id, user_id, role) VALUES
    (1, 1, 'OWNER'),
    (1, 2, 'MEMBER'),
    (2, 3, 'OWNER'),
    (2, 4, 'MEMBER');

-- ─────────────────────────────────────────────────────────────
-- categories — 우리집 (22개, docs/account.md §2.3)
-- ─────────────────────────────────────────────────────────────
INSERT INTO categories (household_id, name, type, sort_order) VALUES
    -- 수입 (4)
    (1, '본인 월급',      'INCOME',   10),
    (1, '아내 월급',      'INCOME',   20),
    (1, '보너스/성과급',  'INCOME',   30),
    (1, '기타 수입',      'INCOME',   40),
    -- 고정지출 (5)
    (1, '주거',           'FIXED',   110),
    (1, '통신',           'FIXED',   120),
    (1, '보험',           'FIXED',   130),
    (1, '구독',           'FIXED',   140),
    (1, '부모님 용돈',    'FIXED',   150),
    -- 변동지출 (8)
    (1, '식비',           'VARIABLE', 210),
    (1, '외식/카페',      'VARIABLE', 220),
    (1, '교통/주유',      'VARIABLE', 230),
    (1, '문화/데이트',    'VARIABLE', 240),
    (1, '의류/미용',      'VARIABLE', 250),
    (1, '의료/건강',      'VARIABLE', 260),
    (1, '경조사',         'VARIABLE', 270),
    (1, '기타 변동',      'VARIABLE', 280),
    -- 투자/저축 (5)
    (1, '비상금',         'INVEST',   310),
    (1, '청년미래적금',   'INVEST',   320),
    (1, '본인 ISA',       'INVEST',   330),
    (1, '아내 ISA',       'INVEST',   340),
    (1, 'KB 직투',        'INVEST',   350);

-- ─────────────────────────────────────────────────────────────
-- categories — 테스트가구 (5개, 격리 검증용 의도적 차이)
-- ─────────────────────────────────────────────────────────────
INSERT INTO categories (household_id, name, type, sort_order) VALUES
    (2, '테스트수입', 'INCOME',   10),
    (2, '테스트주거', 'FIXED',   110),
    (2, '테스트구독', 'FIXED',   120),
    (2, '테스트식비', 'VARIABLE', 210),
    (2, '테스트저축', 'INVEST',   310);
