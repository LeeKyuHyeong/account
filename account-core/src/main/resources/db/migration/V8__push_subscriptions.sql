-- V8__push_subscriptions.sql
--
-- Web Push 구독 (푸시 알림 0단계, 2026-06-04):
--   push_subscriptions — 브라우저 Push API 구독 정보 (endpoint + 암호화 키 쌍).
--   사람+브라우저(기기) 단위 자산이라 가구가 아닌 user 에 귀속 — User 와 같은 비격리군
--   (@Filter 미적용). 조회는 코드에서 항상 user_id 가드 (findByUserId*).
--   endpoint 는 푸시 서비스(FCM/APNs/Mozilla)가 발급한 URL — 브라우저당 유일.
--   발송 시 404/410 응답이면 만료 구독이므로 코드가 행을 삭제한다.

CREATE TABLE push_subscriptions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    endpoint    VARCHAR(512) NOT NULL,
    p256dh      VARCHAR(255) NOT NULL,
    auth        VARCHAR(255) NOT NULL,
    user_agent  VARCHAR(255) DEFAULT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_push_endpoint UNIQUE (endpoint),
    CONSTRAINT fk_push_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_push_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
