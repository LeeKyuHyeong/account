# 세션 만료 후 푸시 "알림 켜기" 가 저장되지 않았는데 "켜짐" 으로 표시
- 일자: 2026-09-19
- 유형: 버그
- 우선순위: P1 (푸시 구독/발송)
- 판정: 조건부 — 자동 검증 ✅, 실제 브라우저 확인 🙋 1건, 운영 배포 ⬜

## 1. 요청과 목적
- 사용자가 원한 것: quiz 에서 고친 세션 계열 결함이 다른 서비스에도 있는지 점검하고 같은 것은 고친다(2026-09-19 조사, 범위 결정 (a)).
- 발견: 푸시 설정 화면(`push/settings.html`)의 `post()` 가 응답을 확인하지 않는다. 세션이 끝난 뒤 "알림 켜기" 를 누르면 브라우저에는 푸시 구독이 생기고 서버에는 저장되지 않는데 화면은 "켜짐" — 사용자는 알림이 안 오는 이유를 알 수 없다. "테스트 알림" 은 실패 응답에 `res.json()` 을 호출해 조용히 죽는다.
- 진행 중 둔 가정:
  - 실패를 알린 뒤 401 이면 1.5초 후 `/login` 으로 보낸다.
  - 서버 저장에 실패한 구독은 브라우저에서도 해제한다(다음 방문 때 "켜짐" 으로 보이지 않게).
  - 끄기 실패 시에는 브라우저 구독을 유지한다(서버에 남은 구독과 어긋나지 않게).

## 2. Acceptance Criteria
| # | 구분 | 조건 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | 예외 | 세션이 끝난 상태에서 "알림 켜기" → "켜짐" 으로 표시되지 않고 실패 사유가 보이며 로그인 화면으로 간다. 브라우저 구독도 남지 않는다 | ✅ 로직 / 🙋 브라우저 | Node 스크래치 하네스(저장소 밖) 1번 — 수정 전 템플릿에서는 같은 하네스가 실패 / 수동 시나리오 1 |
| 2 | 예외 | 서버가 5xx 등 실패를 돌려줘도 "켜짐"·"요청됨" 으로 표시하지 않는다 | ✅ 로직 | 하네스 2·5번 |
| 3 | 정상 | 로그인 상태에서 켜기·끄기·테스트·요약·결산은 이전과 같다 | ✅ 로직 / 🙋 브라우저 | 하네스 3번, `SecurityConfigEntryPointTest#pushApi_withLogin_passes` |
| 4 | 예외 | 끄기가 실패하면 브라우저 구독을 지우지 않고 실패를 알린다 | ✅ 로직 | 하네스 4번 |
| 5 | 권한 | 미인증 `POST /web/push/**` 는 401 — CSRF 토큰이 유효한 경우와, 세션과 함께 토큰도 사라진 경우 모두 | ✅ | `SecurityConfigEntryPointTest#pushApi_withoutLogin_returns401`·`#pushApi_withStaleCsrfToken_returns401` (수정 전 2건 실패 확인) |
| 6 | 권한 | 로그인한 사용자의 CSRF 불일치는 여전히 403 | ✅ | `#pushApi_withLoginButInvalidCsrf_isForbidden` |
| 7 | 연쇄 | 푸시 API 밖의 미인증 요청은 이전처럼 `/login` 으로 간다 (Accept 헤더 유무 무관, 푸시 설정 화면 GET 포함) | ✅ | `#pageNavigation_withoutLogin_redirectsToLogin`·`#otherRequests_withoutLogin_stillRedirectToLogin` |

## 3. 변경 사항
- `account-api/.../config/SecurityConfig.java` — `exceptionHandling`: `POST /web/push/**` 미인증이면 401(`HttpStatusEntryPoint`), 접근 거부 핸들러에서도 미인증이면 401·로그인 상태면 403. 그 밖의 요청은 명시적 기본값으로 기존 동작 유지(`LoginUrlAuthenticationEntryPoint("/login")`, `AccessDeniedHandlerImpl`)
- `account-api/.../templates/push/settings.html` — `post()` 가 401 은 로그인 만료로, 그 외 실패는 오류로 던진다. 버튼 핸들러 5개가 실패를 화면에 표시. 켜기 실패 시 브라우저 구독 롤백
- `account-api/src/test/.../config/SecurityConfigEntryPointTest.java` (신규) — 이 저장소 첫 보안 체인 테스트(MockMvc + `SecurityConfig` 만 띄운 컨텍스트, DB 불필요)
- DB·설정 변경: 없음 (마이그레이션 없음)

## 4. 영향 범위 분석
- `fetch(` 사용처 전수: `push/settings.html` 1곳뿐(`grep -rn "fetch(" account-api/src/main/resources`). 다른 화면은 전부 폼 제출이라 302 가 맞다
- `/web/push/**` POST 엔드포인트 5개(`WebPushController`: subscribe·unsubscribe·test·digest-now·closing-now) — 모두 위 화면에서만 호출
- **조사 때의 추정 정정**: 세션 만료 시 fetch 가 "302 로그인 HTML" 을 받는다고 봤으나, 실제로는 세션과 함께 CSRF 토큰이 사라져 `CsrfFilter` 가 인증 검사보다 먼저 403 을 준다(테스트로 확인). 그래서 엔트리 포인트만이 아니라 접근 거부 핸들러도 함께 처리했다
- 격리 엔티티·`AccountPrincipal`·`SessionHouseholdContextFilter` 변경 없음

## 5. 실행한 검증
| 계층 | 명령/방법 | 결과 | 상태 |
|---|---|---|---|
| 빌드 + 전체 테스트 | `./gradlew build` | 성공, 16 클래스 77건(기존 71 + 신규 6) 실패 0 | ✅ |
| 보안 체인 | `./gradlew :account-api:test --tests "*SecurityConfigEntryPointTest"` | 6 passed (수정 전 2건 실패 → 엔트리 포인트만 넣은 중간 단계 1건 실패 → 통과) | ✅ |
| 화면 JS 로직 | Node 스크래치 하네스 — 템플릿의 실제 인라인 스크립트를 추출해 브라우저 API 스텁으로 실행. **저장소 밖** | 5 checks passed, 수정 전 템플릿(`git show main:`)에서는 1번에서 실패 | ✅ |
| 시크릿 스캔 | CLAUDE.md §5 명령 | 출력 없음 | ✅ |
| 사용자 시나리오 | 아래 6번 | 확인 대기 (카카오 로그인 필요 — 에이전트 불가) | 🙋 |
| 배포 후 Smoke | 미배포 | — | ⬜ |

## 6. 수동 확인 시나리오
1. [전제] 로컬 기동 후 카카오 로그인 → 더보기 → 알림 설정. 알림이 꺼진 상태. [행동] 개발자도구 Application → Cookies 에서 `JSESSIONID` 삭제 → "알림 켜기". [기대] 상태가 "켜기 실패 — 로그인이 만료됐어요…" 로 바뀌고 약 1.5초 뒤 로그인 화면. 다시 로그인해 들어오면 "꺼짐".
2. [행동] 정상 로그인 상태에서 켜기 → 테스트 알림 → 끄기. [기대] 이전과 동일하게 동작.
- 결과: ☐ 통과 ☐ 실패

## 7. checklist 점검
- 점검함: 호출처 전수(fetch 1곳, POST 5개) / 비로그인 직접 호출(AC 5) / 로그인 상태 CSRF(AC 6) / 실패 사유 표시(AC 1·2·4) / 처리 중 상태 — 버튼 중복 클릭은 기존과 동일(변경 없음) / 로컬↔운영 차이 없음(같은 체인)
- 해당 없음: DB·마이그레이션, 격리 엔티티, 입력 경계값, 스케줄

## 8. 발견된 문제와 조치
- 위 1건. 추가한 테스트: `SecurityConfigEntryPointTest` 6건

## 9. 미검증 영역과 남은 위험
- 실제 브라우저 확인(🙋 O-001), 운영 배포(⬜ O-001)
- 같은 조사에서 나온 나머지: `AccountPrincipal` equals/hashCode(다음 작업), 멤버십·역할이 로그인 시점 스냅샷(O-002), `X-Forwarded-For` 첫 값 신뢰(O-003), 초대코드 무기한·합류 스로틀 없음(O-004), 미사용 `jjwt` 의존성(O-005)

## 10. Regression 등록
- R-001, R-002
