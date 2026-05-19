-- V3__seed_dev_bcrypt_passwords.sql
--
-- 시드 사용자 4명의 password_hash 를 placeholder ('BCRYPT_PLACEHOLDER_TASK_5') 에서
-- 실제 BCrypt 해시로 교체. Task 5 JWT 로그인 검증용.
--
-- 평문: "dev1234!"  (의도적으로 약한 dev 전용 — 운영 절대 금지)
-- 알고리즘: BCrypt strength 10 (Spring Security 기본)
-- 생성 방법: account-api/.../tools/BcryptHashToolTest 로 재현 가능
--
-- 4명 모두 동일 해시 사용 (개발 단계 편의). 운영 시드에서는 사용자별 다른 해시 사용.

UPDATE users
   SET password_hash = '$2a$10$toxNw3EOZkgmAGmuEKlzGeqaf4f5zmeT/42WR96bkbrcBvijUVlyu'
 WHERE id IN (1, 2, 3, 4)
   AND password_hash = 'BCRYPT_PLACEHOLDER_TASK_5';
