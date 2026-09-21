# Regression 목록

| ID | 도메인 | 시나리오 | 검증 방법 | 출처 | 등록일 |
|---|---|---|---|---|---|
| R-001 | Auth/Push | 미인증 `POST /web/push/**` 는 401(CSRF 토큰이 사라진 경우 포함), 로그인 상태의 CSRF 불일치는 403, 그 밖의 미인증 요청은 `/login` 리다이렉트 | `SecurityConfigEntryPointTest` | records/2026-09-19_push-api-session-expired | 2026-09-19 |
| R-002 | Push | 푸시 설정 화면은 서버 저장에 실패한 구독을 "켜짐" 으로 표시하지 않는다(브라우저 구독 롤백, 401 은 로그인 화면으로) | 수동: `JSESSIONID` 쿠키 삭제 후 "알림 켜기" (자동 테스트 없음 — 인라인 스크립트) | records/2026-09-19_push-api-session-expired | 2026-09-19 |
| R-003 | Infra/Auth | `/actuator/health` 는 로그인 없이 200(컨테이너 HEALTHCHECK 용 — 302 면 wget 이 `/login` 을 따라가 항상 healthy 가 된다). 그 밖의 `/actuator/**` 는 열리지 않는다 | `SecurityConfigEntryPointTest#actuatorHealth_isOpen_butNothingElseUnderActuator` | records/2026-09-20_api-healthcheck | 2026-09-20 |
| R-004 | Auth | 로그인 로그 IP 는 ForwardedHeaderFilter 를 거친 `getRemoteAddr()` — nginx 가 XFF 를 덮어쓴 요청이면 실 IP, 덧붙인 요청이면 첫 값(위조 가능 → nginx 덮어쓰기 필수) | `OnboardingAwareSuccessHandlerTest` | records/2026-09-21_account-d | 2026-09-21 |
| R-005 | Onboarding | 초대코드 취소는 내 가구 코드만 — 다른 가구 코드 id 는 "초대코드를 찾을 수 없습니다." | `InviteCodeServiceTest#revokeRejectsOtherHouseholdCode` · 로컬 실측 | records/2026-09-21_account-d | 2026-09-21 |
| R-006 | Onboarding | 만료(정각 포함)·취소된 초대코드로는 합류 불가, 만료 코드는 가구 설정 목록에서 빠진다 | `InviteCodeTest` · `HouseholdOnboardingServiceTest` · `InviteCodeServiceTest#listActiveExcludesExpired` | records/2026-09-21_account-d | 2026-09-21 |
