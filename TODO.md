# Account-App TODO

> 앱은 **Thymeleaf SSR 단일 구조로 운영 중** (account.kyuhyeong.com) + CI/CD 가동.
> Flutter→SSR 마이그레이션(M0~M4)은 완료. 이 파일은 이제 **앞으로 할 일** 백로그다 —
> 완료 항목의 상세 diff/사유는 git log / 커밋 메시지에, 전체 설계는
> [`docs/account.md`](docs/account.md)(단일 진실 원천)에 있다.

---

## 열린 항목 (TODO)

### 기능
- [ ] **거래 검색 (상점명·메모)** — `transactions/list` 필터에 keyword 필드. `Specification` 에 LIKE %keyword% (merchant OR memo)
- [ ] **구독 Phase 2 — 실결제 (유예)** — Toss/PortOne/Stripe 정기결제: 빌링키, 웹훅(갱신/실패), 결제 이력, 별도 `Subscription` 엔티티(기간/상태). 외부 PG 계정·시크릿·HTTPS 웹훅 필요

### 푸시 알림 (Web Push — 2026-06-04 계획 확정)

> 채널 결정: **Web Push (VAPID + Service Worker)** — 네이티브 앱 없이 무료로 가능 (FCM/알림톡 배제).
> ⚠ iOS 는 "홈 화면에 추가"(PWA 설치) 한 사용자만 수신 (iOS 정책, 우회 불가). Android Chrome 은 브라우저만으로 OK.
> 의존성: `nl.martijndwars:web-push` 1개 + VAPID 키 쌍(시크릿 — env/`application-secret.yml`).

- [x] ~~**0단계 — 푸시 기반**~~ → **2026-06-04 구현** (로컬 테스트 알림 수신 검증): V8 `push_subscriptions` + `PushSubscription`(비격리 — User 군, user_id 코드 가드) + `PushSubscriptionService`(endpoint upsert/재바인딩) + `PushSendService`(VAPID, 404/410 자동 정리, 키 미설정 시 비활성) + `/web/push` 알림 설정(켜기/끄기/테스트) + `static/sw.js`. 운영 `.env.prod` 에 VAPID 키 입력 완료 — 다음 배포에서 반영
- [x] ~~**1단계 — 이벤트 알림**~~ → **2026-06-04 구현** (⚠ 완전 검증은 운영에서 2인 2기기 필요 — 본인+아내 폰): ① 배우자 거래 알림 — 수동 입력 + 확정(영수증 컨펌 포함) 시 `sendToHouseholdExcept` 로 행위자 제외 발송, 단순 수정·반복거래 자동발화는 제외(컨트롤러 훅이라 자연 배제) ② 새 멤버 합류 알림 (`join` 성공 시 기존 멤버에게)
- [x] ~~**2단계 — 스케줄 알림**~~ → **2026-06-04 구현** (로컬 검증 — `/web/push/digest-now` 수동 발송): ③ 일일 영수증 분석 요약 ④ DRAFT 미확정 리마인더 — `PushDigestService` + 매일 KST 21:00 `PushDigestScheduler` (가구 순회는 반복거래 패턴, 내용 없으면 무발송). **account-batch 이전 검토 결과 = 보류** — batch 는 core 에만 의존하는 정책인데 본 잡은 api 의 푸시/요약 서비스 필요 (스케줄러 javadoc 에 기록, 정책 변경은 별도 승인 사안)
- [x] ~~⑦ 월간 결산 요약 / ⑧ AI 한도 임박~~ → **2026-06-04 구현**: ⑦ 매월 1일 KST 09:00 지난달 결산 푸시 ("N월 결산: 수입·지출·잉여", `/web/report` 딥링크, 거래 없으면 무발송, `/web/push/closing-now` 수동 검증) ⑧ 인제스천 성공 시 "2회 남음"/"소진" 임계 정확히 1회씩 발화 (월 카운트 단조 증가라 중복 방지 상태 불필요)
- ~~⑤ 예산 임박/초과 알림~~ — **2026-06-04 사용자 결정으로 제외** (푸시로 알릴 필요 없음 — 홈 배너로 충분)
- [ ] (후순위) ⑥ 반복 거래 발화 알림

### 운영
- [ ] **운영 DB 시드 전체 제거** — 카카오로 새 가구 시작 → 시드 가구 1·2 + 유저 1~4 전부 삭제, 본인 실 카카오 가구만 보존. 절차: [`data-cleaning.md`](data-cleaning.md). ⚠ 아직 미적용 (비가역 — 백업 필수)
- [ ] **root 비밀번호 SSH 차단** — 키 로그인 확립됨 → `/etc/ssh/sshd_config` `PasswordAuthentication no` (키 검증 후)
- [ ] (선택) GitHub `production` 환경 Required reviewer 등록 → `ci.yml` 의 `deploy` 잡 승인 게이트 활성화

---

## 상시 / 가로지르는 규칙

**보안 / 시크릿 (모든 커밋 전)**
- `git diff --cached | grep -iE "sk-ant|password.*=.*[a-zA-Z0-9]{8}|secret.*=.*[a-zA-Z0-9]{16}"` 결과가 비어있는지 확인
- `application-secret.yml` 미커밋 확인
- **CSRF** — webChain 은 CSRF 활성. 모든 POST 폼에 `_csrf` 히든 필드. 임의로 끄지 말 것

**테스트 정책**
- 컨트롤러 추가 시 MockMvc / `@SpringBootTest` 통합 (DB 는 Testcontainers, H2 금지) + AssertJ
- 격리 검증은 가구 2개 세션으로 (owner1 → 22 카테고리, owner2 → 5, 익명 → `/login`). 세션 경로 격리 회귀 테스트는 미작성 — Testcontainers Windows 비호환이라 Linux CI 전제, 현재는 수동 검증

**폼/컨트롤러 컨벤션** — `Web*Controller` = 얇은 어댑터. record DTO + `@ModelAttribute` + `@Valid` → `BindingResult`, 에러 시 `form`(원본 Map) + `errors`(필드에러 Map) 재렌더. `th:field`/`@Setter` 안 씀. 단건 조회는 `findById` 금지(격리 누수) → `findOne(Specification)`. 상세는 [`CLAUDE.md`](CLAUDE.md)

---

## 완료 이력 (요약)

상세 diff/사유는 git log + 커밋 메시지. 날짜는 작업 시점.

- **M0~M4 마이그레이션** (2026-05-26~27) — Flutter 8개 화면 그룹 → Thymeleaf SSR 전부 이전. `webChain`(세션+formLogin+CSRF) + `CustomUserDetails` + `SessionHouseholdContextFilter` + layout/navbar fragments. M4 에서 JWT/REST(`/api/**`)/apiChain/`flutter_app` 전부 제거 → 순수 SSR 단일화. **2026-05-27 운영 배포**(account.kyuhyeong.com) + CI/CD(`ci.yml` — build+test → deploy 단일 워크플로우)
- **핵심 화면** (M1~M3) — 홈(이번 달 수입/지출/잉여/투자 + 예산 초과 배너), 거래(목록·필터·페이지네이션 / 입력 / 수정 / soft-delete + 변경이력), 영수증(업로드 → Claude 분석 → 신뢰도 분기 컨펌, 전체필드 편집), 추이·예산·순자산(인라인 편집). `findById` PK 직접 로드 격리 누수를 `findOne(Specification)` 으로 다수 수정 (거래/순자산)
- **폰 UX** (2026-05-28, PR A~H) — 숫자 키패드 `inputmode`, 영수증 촬영 FAB, navbar 햄버거+active, 거래 필터 collapsible, flash 메시지 일관화, 인라인 저장 후 스크롤 위치 보존, 다크모드(`data-bs-theme`). `#httpServletRequest` SpEL 이슈는 `ViewContextAdvice`(`currentUri` 주입)로 해결
- **기능 보강** (2026-05-28) — 관리자 페이지(`/web/admin`, OWNER 전용, 멤버 목록 + 비번 재설정), 카테고리 관리 UI(`/web/categories`, 삭제 안전 가드), 반복 거래(V4 + 매일 KST 05:00 스케줄러, 멱등)
- **2026-05-30** — 구독 플랜 티어 Phase 1(FREE/FAMILY/PRO, 영수증 AI 월 한도 게이팅, V5 PERSONAL→FREE), 기간/연 결산(`/web/report`), 거래 CSV 내보내기(UTF-8 BOM), dead 도메인 정리(`monthly_summaries`·`wedding_items` + 월말집계 잡 제거, V6), 문서 전체 현행화
- **2026-06-04** — **영수증 분석 안정화** (실영수증 e2e 검증): 서버측 다운스케일(`ImageDownscaler`/Thumbnailator, 1568px·3MB, EXIF 보정 — API 5MB 한도 + 비용 대응), 429/529/5xx 선형 백오프 재시도, temperature 0, `stop_reason=max_tokens` 잘림 감지(max-tokens 2048), connect/read 타임아웃 분리, `ACCOUNT_CLAUDE_*` env 외부화 + **기본 모델 sonnet-4-6**. 프롬프트: 전자전표/VAN사(Smartro) 머리말 제외 + 종이 전표 배치 규칙 + few-shot (실패 케이스를 `data/receipts/` 원본 이미지로 직접 디버깅 — confidence 0.4→0.9). **분석 정확도 이력 화면** `/web/receipts/analysis` (AI 원본 vs 저장값 필드별 비교 + 수정 횟수/7일 추이, on-the-fly 집계 — 프롬프트 튜닝 루프의 입력). 업로드 화면 카메라/갤러리 버튼 분리(`capture` 토글)
- **2026-06-02** — **카카오 OAuth2 로그인 전환**: formLogin/BCrypt → `oauth2Login`, `CustomUserDetails`→`AccountPrincipal`(OAuth2User), `KakaoOAuth2UserService`(user find/create + dev seed-link), V7(users provider 필드 + email/password_hash nullable, `invite_codes` 테이블). **가구 온보딩**(생성 / 초대코드 가입 + 기본 카테고리 시더, `WebOnboardingController` + `SessionHouseholdContextFilter` 온보딩 가드)으로 회원가입·초대 흐름 실현 → 기존 v1.1 유예 항목 해소. OWNER 초대코드 발급 UI(+ tap-to-copy), admin 비번재설정 제거, 하단탭 web-app 셸 + 카카오 랜딩/더보기. 리포트 지출 분포 차트 + 연도별 프리셋, 차트 만-단위 Y축 정리, 영수증 추출 개선(상점/결제수단/시간). CI+deploy 단일 워크플로우 병합, 운영 mariadb 호스트 3311 노출(0.0.0.0 + 방화벽 관리자 IP 제한) → (선택)DBeaver 접근 항목 대체

---

*완료 항목은 체크 후 위 '완료 이력 (요약)' 로 압축 이동한다. 상세는 git/커밋에 남기고, 설계 변경은 `docs/account.md` 에 반영.*
