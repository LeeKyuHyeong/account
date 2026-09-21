# account D 조사 — O-003 XFF · O-004 초대코드 · O-005 jjwt (+ 모바일 웹앱 화면 문의)
- 일자: 2026-09-21 (회사 PC, 조사만 — 코드 변경 없음)
- 유형: 조사 (구현 전 범위 코드 정독 + 동작 실측)
- 우선순위: O-003 P2(감사 로그) · O-004 P1(가구 합류 = 권한) · O-005 P3
- 판정: 미검증 상태 — 구현 전. 결정 대기 5건(§4). **구현·검증은 집 PC 에서**(회사 PC 는 Docker 없음 → `bootRun`·로컬 DB 확인 불가)

## 1. 요청과 목적
- 사용자가 원한 것: career SM `status.md` 09-21 인계의 "다음 작업 1. account D" 착수. 규칙대로 목록·AC 전에 범위 코드를 끝까지 읽는다.
- 추가 요청: 갤럭시 Z Flip 6 에 설치한 웹앱이 폰 크기인데 데스크톱처럼 보인다 — 원인 조사.

## 2. 조사 결과

### 2-1. O-003 — `framework` 전략은 `getRemoteAddr()` 를 XFF 첫 값으로 덮어쓰고, XFF 헤더를 하류에서 지운다
`LoginLogService.resolveClientIp` 주석("`ForwardedHeaderFilter` 는 URL 재구성만 담당하고 `getRemoteAddr()` 를 바꾸지 않는다")은 **사실과 다르다**. 실측(§3 probe, spring-web 6.1.14 = Boot 3.3.5 관리 버전):

| nginx 가 넘긴 값 | 필터 하류 `getRemoteAddr()` | 하류 `X-Forwarded-For` | 하류 `X-Real-IP` |
|---|---|---|---|
| `XFF: 1.2.3.4, 203.0.113.9` (클라이언트가 1.2.3.4 위조) | **`1.2.3.4`** | **null** | `203.0.113.9` |
| `XFF: 203.0.113.9` | `203.0.113.9` | null | `203.0.113.9` |
| 없음 | `127.0.0.1` | null | null |

- `ForwardedHeaderFilter$ForwardedHeaderExtractingRequest` 가 `getRemoteAddr/Host/Port` 를 오버라이드하고 `ForwardedHeaderUtils.parseForwardedFor` 로 값을 만든다 — XFF **첫 값**을 쓴다.
- 필터가 `X-Forwarded-*` 헤더를 제거한다. 호출부 `OnboardingAwareSuccessHandler:51` 의 `request.getHeader("X-Forwarded-For")` 는 필터 하류라 **운영에서 항상 null** → `resolveClientIp` 는 실제로는 늘 `getRemoteAddr()` 분기로만 동작해 왔다. 결과(위조 가능)는 O-003 등록 내용과 같지만 경로가 다르다.
- `LoginLogServiceTest` 의 resolveClientIp 3건 중 XFF 가 non-null 인 2건은 **운영에 존재하지 않는 입력**을 검증한다.
- nginx(`infra/nginx/account.kyuhyeong.com.conf.example`): `X-Forwarded-For $proxy_add_x_forwarded_for`(들어온 헤더에 **덧붙임** → 위조값이 첫 자리에 남음) · `X-Real-IP $remote_addr`(nginx 가 **덮어씀**, 필터도 지우지 않음).
- **quiz 방식(`getRemoteAddr()`)을 그대로 가져오면 안 된다** — quiz 는 `native`(Tomcat RemoteIpValve, 신뢰 프록시 기준)라 안전하지만 account 의 `framework` 는 첫 값을 믿는다.
- 서버 실제 `account.conf` 가 예시 파일과 같은지는 확인 안 함(⬜) — 수정 전 본인 SSH 로 `grep -n "X-Forwarded-For\|X-Real-IP" /etc/nginx/conf.d/account.conf`.

### 2-2. O-004 — 만료는 마이그레이션 불필요. 더 큰 문제는 "다회용·무기한·취소 불가·카톡 공유"
- `invite_codes.expires_at` 컬럼과 `InviteCode.isValid(now)` 의 만료 검사가 **이미 있다**(V7). `InviteCodeService.generate` 가 `null` 을 넘길 뿐 → **Flyway V10 필요 없음**(09-21 인계 메모의 추정 정정).
- 코드는 **다회용**(`used_count` 는 기록용, 제한 없음) · **무기한** · **`revoke()` 호출부 0건**(관리 화면에 발급만 있고 취소 없음) · `admin/users.html` 의 **카카오톡 초대로 공유**된다 → 메시지가 전달되면 회수 수단이 없다.
- `POST /web/onboarding/join` 은 `anyRequest().authenticated()`(카카오 로그인) + CSRF + `requireUserWithoutHousehold`(1인 1가구). 코드 공간 31^8 과 합치면 무차별 대입 스로틀의 실익은 작다 — **만료·취소가 실익**.

### 2-3. O-005 — `account-api/build.gradle.kts:57-60` jjwt 3줄. `io.jsonwebtoken` import 0건 → 삭제 안전.

### 2-4. 모바일 웹앱이 데스크톱처럼 보임 — 코드 문제 아님, 닫힘
- 서버: viewport 메타(`width=device-width, initial-scale=1, viewport-fit=cover`)는 `fragments/head.html` 1곳, **모든 템플릿이 이 fragment 사용**. 운영 `curl /login` 응답에 포함, `Cache-Control: no-store`. manifest `display: standalone`. `sw.js` 에 `fetch` 핸들러 없음(HTML 캐싱 안 함).
- 이 앱은 데스크톱 레이아웃이 없다 — 분기점은 `app.css` 641px 하나(본문 640px·탭바 가운데 정렬). "데스크톱처럼" = 뷰포트가 640px 초과로 잡힘. Z Flip 6 메인 화면 CSS 폭은 약 412px 라 설정으로는 못 넘고, 980px 가 되는 경로는 "데스크톱 사이트" 모드.
- **원인·해결(개발자 2026-09-21)**: Chrome 의 사이트별 "데스크톱 사이트" 설정을 제거하니 앱처럼 정상 표시. 설치된 웹앱이 Chrome 사이트 설정을 공유한다.

## 3. 실행한 검증
| 항목 | 방법 | 결과 | 상태 |
|---|---|---|---|
| 회귀 기준선 | `./gradlew test` (회사 PC) | api 63 · core 4 · ai 12 = **79 passed**, 0 failed, 16 클래스 | ✅ |
| XFF 동작 | 단독 probe(§6)를 spring-web 6.1.13·6.1.14 로 각각 실행 | 두 버전 동일, §2-1 표 | ✅ |
| 운영 viewport | `curl -D - https://account.kyuhyeong.com/login` | 메타 포함, no-store | ✅ |
| 웹앱 표시 | 개발자 실기기(Z Flip 6) 설정 변경 후 확인 | 정상 | ✅ |
| 서버 nginx 실제 설정 | — | 예시 파일만 읽음 | ⬜ 본인 SSH |

회사 PC 참고: Gradle 이 foojay-resolver jar instrumentation 결과를 캐시로 옮기지 못해 3회 실패 → `-Dorg.gradle.internal.instrumentation.agent=false` 로 통과(13분, JDK 21 자동 다운로드 포함). 집 PC 에서는 필요 없을 것으로 보임.

## 4. 결정 대기 (개발자)
| # | 내용 | 제안 |
|---|---|---|
| 1 [필수] | O-003 방향 — (a) 서버 nginx `X-Forwarded-For $remote_addr`(덮어쓰기) + 앱은 `getRemoteAddr()` 만 / (b) 앱이 `X-Real-IP` 우선, 없으면 `getRemoteAddr()` | (a) — 앱 전체의 `getRemoteAddr()` 가 위조 불가가 된다. (b) 는 오염된 `getRemoteAddr()` 가 남아 같은 구멍이 재발할 수 있다 |
| 2 [필수] | 초대코드 만료 기간 | 7일 |
| 3 [필수] | 이미 발급된 무기한 코드 — 그대로 / 일괄 만료 / 취소 UI 로 직접 정리 | — |
| 4 [선택] | 취소(revoke) 버튼을 이번 범위에 | 넣는다(엔티티 메서드는 있음) |
| 5 [선택] | join 스로틀 | 넣지 않는다(§2-2) |

## 5. 집에서 이어갈 순서
1. `git pull` → §4 결정 → AC 작성·확인(규칙: [정상]·[예외]·[권한], O-004 는 "다른 가구 OWNER 는 남의 코드를 취소할 수 없다" 포함).
2. O-003: probe(§6)를 `account-api` 단위 테스트로 옮겨 **현재 동작을 먼저 고정**(위조 XFF → 위조값 기록 = 재현) → 수정 → 기존 `LoginLogServiceTest` 의 non-null XFF 2건은 운영에 없는 입력이므로 **수정·삭제 전에 허가**. 틀린 주석 교체. (a) 면 서버 nginx 변경은 본인 SSH 한 단계씩(백업 → `nginx -t` → reload).
3. O-004: `generate` 에 만료 부여 · (결정 4) 취소 버튼 · (결정 3) 기존 코드 처리. 스키마 변경 없음.
4. O-005: jjwt 3줄 삭제 → 전체 회귀.
5. 로컬 `bootRun` 확인 후 머지 — account 는 무중단 배포가 아니다.

## 6. probe 소스 (재현용)
`spring-web`·`spring-core`·`spring-jcl`·`spring-beans`·`spring-context`·`spring-test`·`tomcat-embed-core` 를 클래스패스에 두고 JDK 17+ 로 실행.

```java
MockHttpServletRequest req = new MockHttpServletRequest("GET", "/web/home");
req.setServerName("account.kyuhyeong.com"); req.setScheme("https"); req.setServerPort(443);
req.setRemoteAddr("127.0.0.1");                                   // nginx
req.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.9");         // 위조값 + nginx 가 덧붙인 실 IP
req.addHeader("X-Real-IP", "203.0.113.9");
req.addHeader("X-Forwarded-Proto", "https");
new ForwardedHeaderFilter().doFilter(req, new MockHttpServletResponse(), (rq, rs) -> {
    HttpServletRequest r = (HttpServletRequest) rq;
    // r.getRemoteAddr() == "1.2.3.4", r.getHeader("X-Forwarded-For") == null, r.getHeader("X-Real-IP") == "203.0.113.9"
});
```
