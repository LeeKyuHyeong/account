# README·CLAUDE.md·deployment.md 문서 드리프트 정정 (코드 무변경)
- 일자: 2026-10-27
- 유형: 문서 수정 (코드·설정 변경 없음)
- 우선순위: P3 (문구) — 전역 규칙: 빌드 + 확인
- 판정: 수용 가능 — 항목별 "문서 ↔ 코드" 대조 ✅, 빌드·전체 테스트 ✅ (2026-10-27)

## 1. 요청과 목적
- 사용자가 원한 것: 여행 주간 d2 — career `02-projects/account.md` §6 드리프트 표의 행 3·21·24 가 가리키는 코드 쪽 문서(README·CLAUDE.md·`docs/deployment.md`)의 틀린 문장을 코드 실측값으로 고친다.
- 범위 밖(이번에 하지 않음): `docs/account.md` 전면 갱신(§6 행 2, 반나절 분량) · `account-ai/src/main/resources/application.yml` 의 `claude-sonnet-4-5`(§6 행 5, 설정 결함 — 문서가 아니라 코드) · 커밋 트레일러·주석 드리프트(행 8·18·20).
- 배포 영향: `ci.yml` `paths-ignore` 가 `**.md`·`docs/**` 라 이 커밋은 CI·배포를 타지 않는다(main 직행).

## 2. Acceptance Criteria — 항목별 대조
| # | 파일:줄 | 고치기 전 | 코드 실측(2026-10-27, `8f0e71e`) | 고친 뒤 | 상태 |
|---|---|---|---|---|---|
| 1 | `README.md:21` | Entity 11 · Flyway(V1~V6) | `account-core/.../entity/*.java` 에 `@Entity` 15개(Asset·Category·Household·HouseholdMember·InviteCode·JobRun·Liability·LoginLog·MerchantHistory·PushSubscription·Receipt·RecurringTransaction·Transaction·TransactionHistory·User) · `db/migration/V1~V9` 9파일 | Entity 15 · Flyway(V1~V9) | ✅ |
| 2 | `README.md:27` | 관리자(멤버 목록 + 비번 재설정) | `WebAdminController`: `GET /web/admin`, `POST /web/admin/invite`, `POST /web/admin/invite/{id}/revoke`. `AdminUserService.java:23` "비밀번호 재설정 기능은 제거됨" | 가구 설정(OWNER 전용 — 멤버 목록 + 초대코드 발급/취소) | ✅ |
| 3 | `README.md:35` | Sonnet 4.5 | `ClaudeProperties.java:44` 기본값 `claude-sonnet-4-6`, `account-api/.../application.yml:116` `${ACCOUNT_CLAUDE_MODEL:claude-sonnet-4-6}` | 기본 모델 `claude-sonnet-4-6`, `ACCOUNT_CLAUDE_MODEL` 로 교체 가능 | ✅ |
| 4 | `README.md:73` | Flyway V1~V6 · 4계정 비번 dev1234! | V1~V9 · `V2__seed_dev.sql` users 4행·households 2행·categories 22+5행 · 비번 로그인 없음(`SecurityConfig` 는 `oauth2Login` 만, `DevKakaoLinkProperties` = `account.dev.kakao-links`) | V1~V9 · 가구 2 + 유저 4 + 카테고리 22/5 · 시드 계정은 `account.dev.kakao-links` 로 카카오 연결 | ✅ |
| 5 | `CLAUDE.md:259` | 이 저장소는 **private**(2026-09-12 전환) | GitHub API `repos/LeeKyuHyeong/account` → HTTP 200, `"private": false` (비인증 조회) | **public**(09-12 private → 09-16 히스토리 재작성 후 public 재전환), Deploy key 는 그대로 | ✅ |
| 6 | `CLAUDE.md:266` | Gradle 멀티모듈(core/api/ai/batch) | `settings.gradle.kts` include 4개, `account-batch` 자바 파일 0개(`find account-batch -name '*.java'` → 0) | core/api/ai + 빈 batch 모듈 | ✅ |
| 7 | `docs/deployment.md:34` | 저장소가 private 이라 … HTTPS clone/fetch 는 인증 없이 거부된다 | 위 5 와 같음. 09-16 fetch 실패는 private 시절 사실이므로 도입 경위로 남김 | Deploy key 로 받는다(public 재전환 뒤에도 그대로 동작) | ✅ |

## 3. 변경 사항
- `README.md` 4곳, `CLAUDE.md` 2곳, `docs/deployment.md` 1곳 — 문장 교체만. 코드·설정·스키마 변경 없음.
- `docs/verification/records/2026-10-27_docs-drift.md`(이 파일), `docs/verification/README.md` 인덱스 1줄.

## 4. 영향 범위 분석
- `ci.yml` `on.push.paths-ignore`: `**.md`·`docs/**`·`.gitattributes` → 이 커밋은 워크플로 미실행, 운영 배포 없음.
- 문서 간 참조: `CLAUDE.md:259` 가 가리키는 `docs/deployment.md` §2 를 같은 커밋에서 맞췄다. career 쪽 `02-projects/account.md` §6 행 3·21·24 는 귀국 후 기준 커밋 갱신 때 "해소" 로 바꾼다(여행 주간 계획 §6-2).

## 5. 실행한 검증
| 항목 | 상태 | 근거 |
|---|---|---|
| 항목별 문서 ↔ 코드 대조 7건 | ✅ PASS | §2 표 (grep·find·GitHub API 출력) |
| 빌드 + 전체 테스트 `./gradlew test --rerun-tasks` | ✅ PASS | 2026-10-27 15:56 집 PC, `BUILD SUCCESSFUL in 23s`, 91건 통과(core 8 · api 71 · ai 12, failures 0 · errors 0 · skipped 0, `build/test-results` XML 합산). 문서만 바꿨으므로 결과는 10-22 기준선(91건)과 동일 |
| 배포 미발생 | 🙋 USER-CHECK | push 뒤 GitHub Actions 에 새 run 이 **없어야** 정상(`paths-ignore`) |
| `docs/account.md` 전면 갱신 | ⬜ NOT VERIFIED | 범위 밖(§1) — §6 행 2 는 그대로 열림 |

## 6. 남은 것
- `account-ai/src/main/resources/application.yml:28` `claude-sonnet-4-5` 와 `account-api` yml 의 `4-6` 공존(§6 행 5) — 실제 로드 파일 확인 뒤 정리. 코드 변경이라 여행 중 main 직행 대상이 아님.
