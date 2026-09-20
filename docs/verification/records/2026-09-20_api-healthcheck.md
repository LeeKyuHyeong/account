# account-api 컨테이너 HEALTHCHECK (Actuator health, DB 연결 포함)
- 일자: 2026-09-20
- 유형: 기능 (운영 감시)
- 우선순위: P1 (인증 경로 매처에 1줄 추가 — 인증 자체의 동작은 바꾸지 않음)
- 판정: 수용 가능 — 자동 검증·로컬 기동·서버 nginx·운영 배포 확인 ✅ (2026-09-20)

## 1. 요청과 목적
- 사용자가 원한 것: dashboard 가 HEALTHCHECK 결과를 판정에 넣게 됐으므로(dashboard records/2026-09-20), HEALTHCHECK 가 없던 `account-api` 에 DB 연결까지 보는 HEALTHCHECK 를 단다.
- 배경: `account-api` 는 컨테이너가 떠 있기만 하면 dashboard 에서 UP 이었다. DB 연결을 잃어 모든 화면이 500 이어도 보이지 않는다. 이 앱에는 Actuator 도 공개 헬스 경로도 없었다.
- 개발자 확인 결과(결정 사항, 2026-09-20): `/login` 을 찌르는 방식(DB 미확인)이 아니라 DB 까지 확인하는 방식으로.
- 진행 중 둔 가정
  - 운영 VPS 에서 앱 기동(Flyway 검증 포함)이 120초 안에 끝난다 (`start_period`). 넘으면 기동 중에 unhealthy 로 잠깐 보일 뿐 동작에는 영향 없다(docker 는 unhealthy 로 재시작하지 않는다).

## 2. Acceptance Criteria
| # | 구분 | 조건 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | 정상 | 로그인 없이 `GET /actuator/health` 가 200 이다 (302 로그인 리다이렉트면 wget 이 `/login` 200 을 따라가 **항상 healthy** 가 된다) | ✅ | `SecurityConfigEntryPointTest#actuatorHealth_isOpen_*` (허용 규칙을 빼면 실패하는 것 확인) |
| 2 | 권한 | 그 밖의 `/actuator/**` 는 열리지 않는다 — 미인증이면 로그인으로 | ✅ | 같은 테스트 (`/actuator/env`) |
| 3 | 노출 | 응답 본문은 `{"status":...}` 뿐 — 구성 요소·DB 정보 없음 | ✅ | 로컬 기동 2026-09-20: 본문 `{"status":"UP"}` |
| 4 | 정상 | 앱이 Actuator 를 넣고 정상 기동한다 | ✅ | 로컬 `bootRun` 8.2초 기동, 로그 `Exposing 1 endpoint beneath base path '/actuator'`, `/actuator/env` 302 |
| 5 | 예외 | DB 에 못 붙으면 `/actuator/health` 가 503 → 컨테이너 unhealthy | ✅ | 로컬: `docker compose stop mariadb` → `503 {"status":"DOWN"}`, 다시 켜면 `200 UP` 으로 복구 |
| 6 | 노출 | 외부에서 `https://account.kyuhyeong.com/actuator/health` 는 403 | ✅ | 개발자 SSH 2026-09-20: `account.conf` 443 블록(`location /` 앞)에 추가, 백업 `account.conf.bak-<시각>`, `nginx -t` 통과 → reload → `actuator=403`·`login=200`. 배포 후 외부에서도 403 |
| 7 | 정상 | 배포 후 `account-api` 가 `healthy`, dashboard 카드 UP | ✅ | `c9d66aa` 배포(Actions run 35517624764 성공, 교체 중 502 약 20초). 개발자 SSH: `account-api Up 4 minutes (healthy)`. dashboard 카드 UP/running |
| 8 | 연쇄 | 기존 화면·로그인·푸시 API 응답은 그대로 | ✅ | 전체 회귀 79건 |

## 3. 변경 사항
- `account-api/build.gradle.kts` — `spring-boot-starter-actuator`
- `application.yml` — `management.endpoints.web.exposure.include: health` (health 하나만)
- `SecurityConfig` — `/actuator/health` permitAll 1줄
- `docker-compose.prod.yml` — `account-api` healthcheck (`wget --spider http://127.0.0.1:8080/actuator/health`, interval 30s·retries 3·start_period 120s)
- `infra/nginx/account.kyuhyeong.com.conf.example` — `location /actuator { deny all; }` (443 블록)
- DB·설정 변경: 스키마 없음. **서버 nginx `account.conf` 에 위 1줄을 직접 넣어야 한다**(예시 파일은 서버에 자동 반영되지 않는다)

## 4. 영향 범위 분석
- 매처 추가는 `anyRequest().authenticated()` 앞의 permitAll 1줄 — 다른 경로의 판정 순서에 영향 없음. 예외 처리(`/web/push/**` 401 규칙)와 겹치지 않는다
- `SessionHouseholdContextFilter` 는 `/web/**` 만 가드 — `/actuator/health` 에 관여하지 않는다
- health 에 자동으로 붙는 구성 요소: `db`(DataSource)·`diskSpace`·`ping`. 메일·Redis 등은 의존성이 없어 붙지 않는다
- account 는 blue/green 이 없다 — 기동 중에는 `starting`(dashboard 는 UP 으로 본다)

## 5. 실행한 검증
| 계층 | 명령/방법 | 결과 | 상태 |
|---|---|---|---|
| 재현(수정 전) | 허용 규칙을 뺀 상태로 `SecurityConfigEntryPointTest` | 새 테스트 1건 실패(302) | ✅ |
| 빌드 + 전체 회귀 | `./gradlew test` | 79 passed (78 + 신규 1), 0 failed | ✅ |
| 로컬 실행 | `docker compose up -d` + `./gradlew :account-api:bootRun` → curl (2026-09-20, §6-1 1~3 전부) | 기동 ✅ · health 200 UP · env 302 · DB 중지 시 503 DOWN · 재기동 후 200 | ✅ |
| 운영 | §6-2·6-3 (2026-09-20) | nginx 403 · 배포 성공 · `/login` 200 · 컨테이너 healthy | ✅ |

## 6. 수동 확인 시나리오
### 6-1. 로컬 기동 (머지 전에 — 개발자 또는 Docker 가 켜져 있으면 에이전트)
1. `docker compose up -d` → `./gradlew :account-api:bootRun`. [기대] 정상 기동.
2. `curl -s -i http://localhost:8085/actuator/health` [기대] `200` + `{"status":"UP"}` (다른 필드 없음). `curl -s -o /dev/null -w "%{http_code}" http://localhost:8085/actuator/env` [기대] `302`.
3. `docker compose stop mariadb` 후 같은 curl. [기대] `503` + `{"status":"DOWN"}`. 끝나면 `docker compose start mariadb`.

### 6-2. 서버 nginx (개발자 SSH — 머지 전에 해 둬도 무해)
`/etc/nginx/conf.d/account.conf` 의 **443 server 블록**에 `location /actuator { deny all; }` 추가 → `nginx -t && nginx -s reload`.
[기대] 배포 후 외부에서(브라우저 UA·GET) `https://account.kyuhyeong.com/actuator/health` → 403.

### 6-3. 배포 후 (기동 3분 뒤)
`docker ps --filter name=account-api --format '{{.Names}} {{.Status}}'` [기대] `account-api Up … (healthy)`. dashboard 의 Account 카드 UP / running.
`(unhealthy)` 면 `docker inspect --format '{{json .State.Health.Log}}' account-api`.

## 7. checklist 점검
- 점검함: 새 공개 경로의 노출 범위(health 하나, 본문 최소, nginx 차단) / 로그인 리다이렉트가 헬스체크를 "항상 성공"으로 만드는 함정 / 배포 방식(blue/green 없음 → 기동 실패 시 사이트 중단, 그래서 §6-1 을 머지 전 조건으로 둠)
- 해당 없음: 가구 격리(격리 엔티티 미접촉), DB 스키마, 화면

## 8. 발견된 문제와 조치
- 없음

## 9. 미검증 영역과 남은 위험
- 로컬 기동은 확인했으나 운영 컨테이너(alpine busybox `wget`)에서의 HEALTHCHECK 동작은 배포 후에야 볼 수 있다(§6-3). 실패해도 표시만 unhealthy 가 되고 서비스에는 영향 없다
- 관찰(이번 범위 밖, O-003 과 같은 계열): `server.forward-headers-strategy: framework` — Spring 의 ForwardedHeaderFilter 가 클라이언트 IP 를 어떻게 정하는지는 O-003 작업에서 확인한다

## 10. Regression 등록
- R-003
