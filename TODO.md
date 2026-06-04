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
- **2026-06-02** — **카카오 OAuth2 로그인 전환**: formLogin/BCrypt → `oauth2Login`, `CustomUserDetails`→`AccountPrincipal`(OAuth2User), `KakaoOAuth2UserService`(user find/create + dev seed-link), V7(users provider 필드 + email/password_hash nullable, `invite_codes` 테이블). **가구 온보딩**(생성 / 초대코드 가입 + 기본 카테고리 시더, `WebOnboardingController` + `SessionHouseholdContextFilter` 온보딩 가드)으로 회원가입·초대 흐름 실현 → 기존 v1.1 유예 항목 해소. OWNER 초대코드 발급 UI(+ tap-to-copy), admin 비번재설정 제거, 하단탭 web-app 셸 + 카카오 랜딩/더보기. 리포트 지출 분포 차트 + 연도별 프리셋, 차트 만-단위 Y축 정리, 영수증 추출 개선(상점/결제수단/시간). CI+deploy 단일 워크플로우 병합, 운영 mariadb 호스트 3311 노출(0.0.0.0 + 방화벽 관리자 IP 제한) → (선택)DBeaver 접근 항목 대체

---

*완료 항목은 체크 후 위 '완료 이력 (요약)' 로 압축 이동한다. 상세는 git/커밋에 남기고, 설계 변경은 `docs/account.md` 에 반영.*
