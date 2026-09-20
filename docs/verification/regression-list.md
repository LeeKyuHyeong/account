# Regression 목록

| ID | 도메인 | 시나리오 | 검증 방법 | 출처 | 등록일 |
|---|---|---|---|---|---|
| R-001 | Auth/Push | 미인증 `POST /web/push/**` 는 401(CSRF 토큰이 사라진 경우 포함), 로그인 상태의 CSRF 불일치는 403, 그 밖의 미인증 요청은 `/login` 리다이렉트 | `SecurityConfigEntryPointTest` | records/2026-09-19_push-api-session-expired | 2026-09-19 |
| R-002 | Push | 푸시 설정 화면은 서버 저장에 실패한 구독을 "켜짐" 으로 표시하지 않는다(브라우저 구독 롤백, 401 은 로그인 화면으로) | 수동: `JSESSIONID` 쿠키 삭제 후 "알림 켜기" (자동 테스트 없음 — 인라인 스크립트) | records/2026-09-19_push-api-session-expired | 2026-09-19 |
| R-003 | Infra/Auth | `/actuator/health` 는 로그인 없이 200(컨테이너 HEALTHCHECK 용 — 302 면 wget 이 `/login` 을 따라가 항상 healthy 가 된다). 그 밖의 `/actuator/**` 는 열리지 않는다 | `SecurityConfigEntryPointTest#actuatorHealth_isOpen_butNothingElseUnderActuator` | records/2026-09-20_api-healthcheck | 2026-09-20 |
