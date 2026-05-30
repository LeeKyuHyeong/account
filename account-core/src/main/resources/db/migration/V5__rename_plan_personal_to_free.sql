-- V5__rename_plan_personal_to_free.sql
--
-- 구독 티어 리네임: PERSONAL → FREE (무료 티어로 제품명 정렬, 2026-05-30).
-- PlanType enum 상수가 PERSONAL → FREE 로 바뀌므로 기존 households.plan_type='PERSONAL' row 를
-- 'FREE' 로 변환하지 않으면 @Enumerated(STRING) 매핑이 깨진다. V1/V2 는 적용 완료된 마이그레이션이라
-- 수정 금지(체크섬 드리프트) — 본 V5 에서 데이터 변환 + 컬럼 기본값 변경을 함께 처리한다.

UPDATE households SET plan_type = 'FREE' WHERE plan_type = 'PERSONAL';

ALTER TABLE households ALTER COLUMN plan_type SET DEFAULT 'FREE';
