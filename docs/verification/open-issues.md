# Open Issues

미검증(⬜)·직접 확인 필요(🙋)·보류 항목. 상태: OPEN / IN-PROGRESS / CLOSED. 닫을 때 해결 기록 링크를 남긴다.

| ID | 상태 | 도메인 | 내용 | 미검증/보류 이유 | 확인 방법 | 등록 | 해결 기록 |
|---|---|---|---|---|---|---|---|
| O-001 | OPEN | Push | 푸시 설정 화면 세션 만료 처리 — 로컬 브라우저 확인(개발자 2026-09-19)·운영 배포·외부 Smoke 완료(`d146844`). 남은 것은 운영에서의 시나리오 재확인(선택) | 카카오 로그인 필요 — 에이전트 불가 | records/2026-09-19_push-api-session-expired §6 → main 머지 → 배포 Smoke | 2026-09-19 | |
| O-002 | OPEN | Auth | 활성 가구·역할·sysAdmin 이 로그인 시점 세션 스냅샷(`SessionHouseholdContextFilter`, `KakaoOAuth2UserService`). 지금은 권한을 낮추는 기능이 없어 악용 경로 없음 — **"멤버 내보내기" 를 만들기 전에** 세션 만료나 요청당 멤버십 재검증을 먼저 설계. 현재 `SessionRegistry` 미등록이라 특정 사용자 세션을 끊을 수단이 없다 | 기능 부재로 잠복 | 해당 기능 설계 시 | 2026-09-19 | |
| O-003 | OPEN | Security | `LoginLogService.resolveClientIp` 가 `X-Forwarded-For` 첫 값을 그대로 신뢰 — 로그인 로그의 IP 위조 가능. 인가·차단에는 쓰이지 않아 영향은 감사 로그 한정 | 범위 밖 | nginx 가 XFF 를 덮어쓰는지 확인 후 결정 (itsm `ClientIpResolver` 참고) | 2026-09-19 | |
| O-004 | OPEN | Onboarding | 초대코드가 만료 없이 발급(`InviteCodeService` `expiresAt = null`), `POST /web/onboarding/join` 스로틀 없음. 코드 공간 31^8 이라 실현성은 낮음 | 범위 밖 | 별도 결정 | 2026-09-19 | |
| O-005 | OPEN | Build | 미사용 `jjwt` 의존성 잔존(M4 에서 JWT 제거) | 무관한 정리 | 별도 작업 | 2026-09-19 | |
| O-006 | OPEN | Infra | account-api HEALTHCHECK(Actuator health): 로컬 기동 확인(앱이 뜨는지·본문·DB 중지 시 503) / 서버 nginx `location /actuator { deny all; }` 추가 / 배포 후 `healthy` 확인 | 작업 시점에 로컬 Docker 가 꺼져 있어 앱을 띄워 보지 못함. 무중단 배포가 아니라 기동 실패 = 사이트 중단 → **로컬 기동 확인 전에는 main 머지 금지** | records/2026-09-20_api-healthcheck §6 | 2026-09-20 | |
