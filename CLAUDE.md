# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

LLM 코딩에서 흔히 발생하는 실수를 줄이기 위한 행동 가이드라인.
Andrej Karpathy의 LLM 코딩 함정 관찰을 기반으로 함.

**Tradeoff:** 이 가이드라인은 **속도보다 신중함**에 무게를 둔다.
사소한 작업(오타 수정, 명백한 한 줄 변경)에는 판단껏 유연하게 적용할 것.

> **단일 진실 원천**: 설계/페이즈는 [`docs/account.md`](docs/account.md), 작업 백로그/우선순위는 [`TODO.md`](TODO.md). 본 파일은 LLM 행동 가이드 + 빌드/아키텍처 요약이며, 두 문서를 대체하지 않는다.

---

## 1. Think Before Coding (코딩 전에 먼저 생각)

**가정하지 마라. 혼란을 숨기지 마라. Tradeoff를 드러내라.**

구현에 들어가기 전에:

- **가정을 명시적으로 진술**한다. 불확실하면 추측하지 말고 질문한다.
- **여러 해석이 가능하면 모두 제시**한다. 조용히 하나만 골라서 진행하지 않는다.
- **더 단순한 접근이 존재하면 말한다.** 필요할 때는 반박(push back)한다.
- **불명확하면 멈춘다.** 무엇이 헷갈리는지 명시하고 묻는다.

### 이 프로젝트에서의 적용 예시

- **새 도메인 Entity 추가 시:** 가구 격리 대상인가? (`@Filter("householdFilter")` 적용 여부) — `User` / `Household` / `HouseholdMember` 만 비격리이며 나머지는 모두 `household_id` + Filter 가 필수. 임의로 빠뜨리면 격리 누수.
- **JWT 클레임 추가/변경 시:** `JwtTokenProvider.issueAccessToken` + `JwtAuthenticationFilter.doFilterInternal` + `AuthService.login/refresh` + 필요 시 `HouseholdContext` 까지 동기화 필요. 한 곳만 고치지 말 것.
- **영수증 분석 실패 보고 받으면:** 어느 layer 인지부터 확인 — `ReceiptStorage` (디스크 IO) / `ReceiptAnalysisService` (Claude 호출+JSON 파싱) / 카테고리 매칭 (`ReceiptIngestionService.resolveCategory`) / `Transaction` insert. 추측 패치 금지.
- **Flutter 폼/상태 추가 시:** 기존이 `reactive_forms` 면 그 패턴, 기존이 Riverpod `AsyncNotifier` 면 그 패턴. 같은 화면에서 두 스타일을 섞지 말 것.
- **격리 검증 통합 테스트 (`HouseholdIsolationIntegrationTest`)는 `@Disabled` 상태이다.** Linux CI 또는 Docker Desktop TCP 노출 전까지는 enable 시도하지 말 것 — Windows + Docker Desktop CLI 프록시 + Testcontainers 비호환은 알려진 이슈.

---

## 2. Simplicity First (단순함이 먼저)

**문제를 해결하는 최소 코드. 투기적인 것은 없다.**

- 요청되지 않은 기능은 추가하지 않는다.
- 1회용 코드에 추상화 계층을 만들지 않는다.
- 요청되지 않은 "유연성"이나 "설정 가능성"을 끼워넣지 않는다.
- 발생할 수 없는 시나리오에 대한 예외 처리를 하지 않는다.
- 200줄로 쓴 것이 50줄로 가능했다면, 다시 쓴다.

자문해라: **"시니어 엔지니어가 이건 오버엔지니어링이라고 할까?"** 그렇다면 단순화한다.

### 이 프로젝트에서의 안티 패턴

- `CategoryResolver` 인터페이스 + 구현체로 분리하지 마라. `ReceiptIngestionService.resolveCategory` 안의 fallback 체인 (정확 일치 → "기타 변동" → 첫 VARIABLE → 첫 카테고리) 정도면 충분.
- Flutter 색상 1개 추가하려고 `colors.dart` / `tokens.dart` / `theme.dart` 3중 분리 X. `AppTheme.light()` 안에 그대로.
- `application-dev.yml` / `application-stg.yml` / `application-prd.yml` 분리는 Week 6 운영 진입 전까지 도입 X. 현재는 `application.yml` + `application-secret.yml` (gitignored) + env 오버라이드.
- `account-batch` 가 비어 있어도 첫 잡 추가 시 `AbstractScheduledJob` 같은 부모 클래스부터 만들지 마라. 한 잡으로 끝나면 한 클래스로 끝낸다.
- **MVP scope (`docs/account.md` §11) 를 임의로 확장하지 마라**: 회원가입/초대 UI, OWNER/MEMBER 권한 차등, 카테고리 커스터마이징 UI, FCM 푸시, 순자산/결혼지출 화면 — 전부 v1.1 / v1.5 / v2 로 유예된 항목.

---

## 3. Surgical Changes (외과적 변경)

**필요한 곳만 건드린다. 내가 만든 흔적만 정리한다.**

기존 코드를 수정할 때:

- 인접한 코드, 주석, 포매팅을 "개선"하지 않는다.
- 망가지지 않은 것을 리팩토링하지 않는다.
- 내가 다르게 작성할 스타일이라도, **기존 스타일에 맞춘다.**
- 무관한 dead code를 발견하면 *언급만* 한다. 삭제하지 않는다.

내 변경이 고아(orphan)를 만들었다면:

- *내 변경*으로 인해 사용되지 않게 된 import/변수/함수만 제거한다.
- 변경 전부터 있던 dead code는 요청 없이 제거하지 않는다.

검증 기준: **변경된 모든 라인은 사용자 요청과 직접 연결되어야 한다.**

### 이 프로젝트에서의 특별 주의

- **`docs/account.md` §11 의 7개 확정 결정** (Java 21, iOS 유예, Claude 키 분리, 영수증 5년 보관, 가구 멤버 모두 수정 + 변경 이력, 홈+카메라 FAB, Multi-tenant) 은 단독 변경 금지. 변경 사유 + 영향 보고 후 사용자 승인.
- **`account-core/build.gradle.kts` 의 "다른 어떤 account-* 모듈에도 의존하지 않음" 정책은 의도된 분리**. `account-ai` 의 인터페이스 (`MerchantHistoryProvider` 등) 를 implements 하는 어댑터가 필요하면 `account-api` 에 배치 (Task 6 의 `JpaMerchantHistoryProvider` 가 그 예).
- **한국어 주석 + Lombok + record 패턴은 기존 코드 컨벤션**. "더 모던하게" 라는 이유로 일관성 깨지 X.
- **다음 설정값은 의도되어 있다 — 무관 작업 중 만지지 마라**: `spring.jpa.hibernate.ddl-auto: validate` (Flyway 단독 책임), `spring.flyway.baseline-on-migrate: false` (드리프트 즉시 발각), `spring.threads.virtual.enabled: true` (Loom), `application.yml` 의 `ACCOUNT_DB_PORT:3305` 기본값 (호스트 mysqld 3306 충돌 회피), bootRun 의 `workingDir = rootProject.projectDir` (루트 `application-secret.yml` 로드 위함).
- **`HouseholdIsolationIntegrationTest @Disabled` 는 환경 이슈**. 코드 자체는 정상이므로 enable 시도 / 대체 H2 마이그레이션 같은 우회 시도 X.

---

## 4. Goal-Driven Execution (목표 주도 실행)

**성공 기준을 정의한다. 검증될 때까지 루프를 돈다.**

작업을 검증 가능한 목표로 변환한다:

| 명령형 지시           | 목표형 변환                                                |
| --------------------- | ---------------------------------------------------------- |
| "validation 추가해줘" | "잘못된 입력에 대한 테스트를 쓰고, 통과시켜라"             |
| "버그 고쳐줘"         | "버그를 재현하는 테스트를 쓰고, 통과시켜라"                |
| "X를 리팩토링해줘"    | "리팩토링 전후로 테스트가 모두 통과하는지 확인하라"        |

다단계 작업은 짧은 계획을 먼저 제시한다:

```
1. [단계] → 검증: [확인 방법]
2. [단계] → 검증: [확인 방법]
3. [단계] → 검증: [확인 방법]
```

강한 성공 기준은 LLM이 독립적으로 루프를 돌게 한다.
약한 기준("동작하게 해줘")은 계속 추가 질문을 요구한다.

### 이 프로젝트에서의 검증 패턴

- **Backend Controller/Service 추가**: `./gradlew :account-api:compileJava` 통과 → `./gradlew test` 통과 → (가능 시) `./gradlew :account-api:bootRun` 후 `curl -H "Authorization: Bearer ..."` 로 200/4xx 응답 확인. 격리 엔티티 조회는 `logging.level.org.hibernate.SQL=DEBUG` 켜고 `WHERE household_id = ?` 가 SQL 에 포함되는지 확인.
- **새 마이그레이션 추가**: `./gradlew :account-api:bootRun` → 기동 로그에 `Migrating schema "account" to version "Vx__..."` + `flyway_schema_history` 에 새 row → 시드/DDL 결과를 직접 SELECT 로 검증.
- **Flutter 작업**: `flutter analyze` 0 issues → `flutter test` 통과 → `flutter build apk --debug` 통과 → (가능 시) 에뮬레이터에서 시나리오 1회 수동 실행. analyze 에 info-level 경고도 0 으로 유지.
- **격리에 영향 줄 수 있는 변경**: `HouseholdIsolationIntegrationTest` 가 Disabled 라 자동 검증이 불가하므로 수동 `curl` 로 토큰 두 개 (owner1 → 22, owner2 → 5, 익명 → 401) 시나리오 재현.
- **시크릿 변경**: `git diff --cached | grep -iE "sk-ant|password.*=.*[a-zA-Z0-9]{8}|secret.*=.*[a-zA-Z0-9]{16}"` 결과가 비어있는지 확인 (`docs/account.md` §10.2 패턴).

---

## 5. 주요 커맨드

전제: 프로젝트 루트 = `D:\account-app`. PowerShell 또는 bash 모두 가능. Gradle 명령은 wrapper (`./gradlew`) 사용.

### 부팅 / 셋업

```bash
# 시크릿 템플릿 복사 (최초 1회) — Claude API 키 + JWT secret base64 필요
cp application-secret.yml.example application-secret.yml
# 그리고 application-secret.yml 의 값 채우기 — 절대 커밋 금지

# MariaDB (호스트 포트 3305, 호스트 3306 은 기존 mysqld 점유)
docker compose up -d

# 백엔드 기동 — Flyway 가 V1__init / V2__seed_dev / V3__... 자동 적용
./gradlew :account-api:bootRun
```

### 빌드 / 테스트

```bash
# 전체 빌드 (테스트 포함)
./gradlew build

# 테스트 제외 빌드
./gradlew build -x test

# 모듈 단독 컴파일 / 테스트
./gradlew :account-api:compileJava
./gradlew :account-ai:test
./gradlew :account-core:test

# 단일 테스트 클래스/메서드
./gradlew :account-ai:test --tests "ReceiptAnalysisServiceTest"
./gradlew :account-ai:test --tests "ReceiptAnalysisServiceTest.parsesCleanJson"
```

### Flutter

```bash
cd flutter_app

# 의존성 동기화
flutter pub get

# 정적 분석 + 테스트 + APK
flutter analyze              # 0 issues 가 목표
flutter test                 # widget test 포함
flutter build apk --debug

# 에뮬레이터 실행 — 백엔드 base URL 기본은 http://10.0.2.2:8080
flutter run
# 운영 / 다른 호스트: --dart-define=API_BASE_URL=https://account.kyuhyeong.com
```

### 시크릿 스캔 (커밋 전 권장)

```bash
git diff --cached | grep -iE "sk-ant|password.*=.*[a-zA-Z0-9]{8}|secret.*=.*[a-zA-Z0-9]{16}"
# 결과가 비어있어야 정상 (변수명 `_passwordController` 같은 false positive 는 무시)
```

---

## 6. 아키텍처 개요

### 6.1 모듈 구조 + 의존성 정책

```
account-core   ←─── account-api ←─── (없음)
   ↑                    ↓
   └── account-batch    └── account-ai
```

- **`account-core`**: 도메인 Entity (12개) + Repository + Multi-tenant 격리 본체 (`HouseholdContext`, `HouseholdFilterAspect`, Hibernate `@Filter`) + Flyway 마이그레이션. **다른 어떤 `account-*` 모듈에도 의존 X** (`build.gradle.kts` 의 강한 정책).
- **`account-ai`**: Claude Vision API 호출 + 프롬프트 조립 + JSON 파싱. **다른 어떤 `account-*` 모듈에도 의존 X**. `MerchantHistoryProvider` 같은 인터페이스만 외부에 공개.
- **`account-api`**: REST 컨트롤러 + JWT 인증 + 영수증 인제스천 흐름. `account-core` + `account-ai` 둘 다에 의존하는 유일한 모듈 — 어댑터 (예: `JpaMerchantHistoryProvider`) 는 이쪽에 배치.
- **`account-batch`**: (Week 4+) 월말 집계 / 이미지 정리. `account-core` 에만 의존, `account-api` 의존 금지.
- **`flutter_app`**: Flutter 클라이언트 (Android-first, iOS 유예). `lib/features/{auth,transaction,home}/` + `lib/core/{network,storage,theme,config}/`. Gradle 모노레포 외부 — 독립 빌드.

### 6.2 Multi-tenant 격리 흐름 (본 프로젝트의 가장 중요한 디자인)

```
1. 클라이언트가 Bearer JWT 첨부 요청
2. JwtAuthenticationFilter 가 토큰 검증 → household_id 클레임을
   HouseholdContext.set(Long) 로 ThreadLocal 바인딩
3. @Transactional 메서드 진입 시 HouseholdFilterAspect 가
   Hibernate Session 에 householdFilter 활성화 (currentHouseholdId = ctx 값)
4. Repository 의 모든 쿼리에 자동으로 WHERE household_id = ? 첨가
5. 응답 직전 finally 블록에서 HouseholdContext.clear()
```

핵심 보장: **`HouseholdContext` 미설정 상태에서 격리 엔티티를 조회하면 `-1` sentinel 로 필터가 켜져 0 rows 반환** (`HouseholdFilterAspect.NO_TENANT_SENTINEL`). 인증 단계 누수에 대한 두 번째 방어선이다 — 임의로 끄지 말 것.

비격리 Entity (Filter 미적용): `User`, `Household`, `HouseholdMember`. 나머지 9개 도메인 Entity는 `@Filter("householdFilter")` 가 클래스에 적용되어 있다.

### 6.3 핵심 흐름

- **JWT 발급**: `AuthController.login` → `AuthService.login` → `JwtTokenProvider.issueAccessToken/issueRefreshToken` (HS256, 15분/30일, claims=`sub`/`household_id`/`role`).
- **영수증 인제스천**: `ReceiptController` → `ReceiptIngestionService.ingest` (@Transactional 단일 트랜잭션) → `ReceiptStorage.store` (디스크) + `Receipt` insert + `MerchantHistoryProvider.getRecentHistory` + `ReceiptAnalysisService.analyze` (Claude) + 카테고리 fallback 매칭 + DRAFT `Transaction` insert.
- **거래 목록**: `TransactionController.list` → `TransactionService.list` → `JpaSpecificationExecutor` 로 동적 필터 (from/to/categoryId/type/status) + `occurred_at DESC`, soft-delete (`deletedAt IS NULL`) 자동 제외.
- **Flutter 인증**: `AuthInterceptor` (Dio) 가 모든 요청에 Bearer 자동 부착 + 401 응답 시 `/api/auth/refresh` 호출 → 원 요청 1회 retry. 토큰은 `flutter_secure_storage` (Android Keystore).
- **Flutter 라우팅**: `go_router` + `_RouterNotifier` (`ref.listen` → `ChangeNotifier` 어댑터) — `AuthState` 변화 시 자동 redirect.

---

## 프로젝트 고유 규칙

위 가이드라인 외에 이 저장소에서 항상 적용되는 규칙:

- **`docs/account.md` 가 단일 진실 원천이다.** 별도 "구현 가이드" / "통합 가이드" / 새 README 같은 문서는 사용자가 명시 요청하기 전엔 만들지 않는다.
- **시크릿 분리 + 절대 커밋 금지** (`application-secret.yml`, `.env`, `*.pem`, `*.key`, `local.properties`). 과거 OAuth 키 노출 사고 재발 방지. 커밋 전 §5 의 시크릿 스캔 명령으로 검증.
- **환경 의존 값 (DB 호스트/포트/계정, API 키, JWT secret, 영수증 저장 경로) 은 전부 환경변수 + `application-secret.yml` 로 외부화**. 코드 하드코딩 금지.
- **Lombok 사용 OK, `@Setter` 절대 금지**. Entity 상태 변경은 비즈니스 메서드 (`confirm()`, `softDelete()`, `markProcessed()` 등). DTO 는 Java 21 `record`.
- **Repository 에 raw SQL / `@Query` 금지**. 메서드 이름 derivation 또는 `JpaSpecificationExecutor`. `@Query` 가 진짜 필요하면 사유를 PR 본문에 적는다.
- **DB 통합 테스트는 Testcontainers + MariaDB 이미지만**. H2 등 인메모리 대체 금지 (방언 차이로 운영과 결과가 달라진다).
- **커밋 메시지는 Conventional Commits**: `feat|fix|refactor|chore|docs|test|build|ci`(`scope`): description. scope 는 `core` / `api` / `ai` / `batch` / `flutter` / `build` / `infra`.
- **커밋 메시지에 AI / Claude 공동 작성 표기 금지**. `Co-Authored-By: Claude`, `🤖 Generated with Claude Code` 같은 trailer / 문구를 넣지 않는다. 커밋 메시지는 변경 내용만 담는다.
- 한국어로 답변/주석 OK. 단 **변수명/함수명/커밋 메시지는 영어** 통일.

---

**이 가이드라인이 잘 작동하고 있다는 신호:**

- diff에 불필요한 변경이 줄어든다 — 요청한 변경만 나타난다.
- 오버엔지니어링으로 인한 재작성이 줄어든다 — 처음부터 단순하다.
- 실수 *후*가 아니라 구현 *전*에 명확화 질문이 온다.
- 깔끔하고 최소한의 PR — 지나가다가 하는 "개선"이 없다.
