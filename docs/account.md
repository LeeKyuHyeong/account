# Account-App

> 부부/가구 단위 가계부 앱. 영수증 사진을 찍으면 Claude Vision API가 OCR + 카테고리 자동 분류 후 저장한다. Multi-tenant(가구 단위) 구조로 처음부터 설계되어 추후 가까운 인원(20명 내외)으로의 확장이 가능.

**Repo**: <https://github.com/LeeKyuHyeong/account-app>
**현재 페이즈**: `Week 1 — 기반 + Multi-tenant 셋업` (☞ §8 참조)
**Last updated**: 2026-05-18

---

## 목차

0. [에이전트 작업 가이드](#0-에이전트-작업-가이드)
1. [프로젝트 컨텍스트](#1-프로젝트-컨텍스트)
2. [엑셀 → 앱 매핑 / 카테고리](#2-엑셀--앱-매핑--카테고리)
3. [핵심 기능: 영수증 OCR + AI 분류](#3-핵심-기능-영수증-ocr--ai-분류)
4. [시스템 아키텍처](#4-시스템-아키텍처)
5. [기술 스택](#5-기술-스택)
6. [데이터 모델 (Multi-tenant ER)](#6-데이터-모델-multi-tenant-er)
7. [API 설계 / 인증 / 보안](#7-api-설계--인증--보안)
8. [✅ 다음 작업: Week 1](#8--다음-작업-week-1) ★ 에이전트가 그대로 진행할 영역
9. [개발 로드맵 (Week 2-6 + 이후)](#9-개발-로드맵-week-2-6--이후)
10. [작업 규칙](#10-작업-규칙)
11. [확정된 결정 사항 (7개)](#11-확정된-결정-사항-7개)
12. [비용 추정](#12-비용-추정)
13. [확장성 / 사업화 가능성](#13-확장성--사업화-가능성)
14. [부록: 재활용 자산 / 환경 정보](#14-부록-재활용-자산--환경-정보)

---

## 0. 에이전트 작업 가이드

이 문서를 작업 지시서로 사용하는 AI 에이전트(Claude Code 등)는 다음 규칙을 따른다.

### 0.1 본 문서의 사용법

- §1~§7은 **참조 영역**. 작업 중 의문이 생기면 해당 절을 찾아 의사결정 근거로 사용.
- §8은 **현재 페이즈 작업 영역**. 여기에 명시된 작업만 우선 진행. 완료 후 §9 다음 페이즈로 넘어가기 전에 사용자 확인.
- §10은 **모든 작업에 적용되는 규칙**. 코드 스타일, 시크릿 관리, 커밋 컨벤션 등.
- §11은 **변경 불가 결정 사항**. 임의로 뒤집지 말 것.

### 0.2 작업 진행 원칙

1. **현재 페이즈(§8)의 작업만 수행**. 다음 페이즈로 진도를 빼지 말 것.
2. **작업 단위로 커밋**. 한 커밋에 여러 페이즈 작업을 섞지 말 것.
3. **시크릿 절대 커밋 금지** (§10.2 참조). 환경변수 또는 `application-secret.yml` 분리.
4. **§11 결정을 임의로 변경하지 말 것**. 변경 필요 시 사용자에게 명시적 확인.
5. **모르는 것은 추측하지 말 것**. 특히 외부 의존(VPS IP, API 키, 도메인) 관련해 모호하면 사용자에게 질문.
6. **테스트 없이 완료 선언 금지** (§10.4).

### 0.3 작업 외 영역

- 본인 PC의 IDE 설정, OS 설정, GitHub Repo Settings(secret 등록 등) 같은 **사람만 할 수 있는 작업**은 에이전트가 직접 수행 불가. 사용자에게 단계별 안내만 제공.
- VPS 운영 명령(SSH 접속, nginx 설정 적용 등)은 사용자가 직접 수행. 에이전트는 적용할 설정 파일/명령어만 제공.

---

## 1. 프로젝트 컨텍스트

### 1.1 배경 및 목적

**현재**: 엑셀 가계부 2종(`우리집_자산관리.xlsx`, `우리집_가계부.xlsx`)으로 부부 자산 관리 중. 매월 말 카드 명세서 보고 카테고리별 합계 수기 입력. 이 과정의 마찰이 본질적 문제.

**앱으로 전환하는 이유**:
- 매월 말 일괄 입력 → **결제 발생 즉시 영수증 촬영**으로 마찰 제거
- 카테고리 분류 수기 → **AI 자동 분류**로 시간 단축
- 단일 엑셀 작성자 → **부부 동시 입력/동기화**
- 정적 시트 → **실시간 대시보드 + 푸시 알림**

**범위**:
- **MVP**: 본인 + 아내 (2명, private)
- **확장 가능성**: 가구(household) 단위 분리, 최대 20명 내외 (친구·가족 가구)
- **플랫폼**: Flutter (iOS/Android), 추후 Web Admin 옵션
- **호스팅**: 기존 kyuhyeong.com VPS 활용 (`account.kyuhyeong.com` 서브도메인 신규 추가)

### 1.2 모노레포 구성 (현재 + 계획)

| 모듈 | 상태 | 책임 |
|---|---|---|
| `account-ai` | ✅ **프로토타입 존재** (이번 페이즈에 멀티모듈 통합) | Claude Vision API 통합, 영수증 OCR + 카테고리 분류 |
| `account-api` | ⏳ §8 Week 1 | REST 엔드포인트, JWT 인증, 가구 격리 진입점 |
| `account-core` | ⏳ §8 Week 1 | Entity, Repository, Service. Multi-tenant 격리의 본체 |
| `account-batch` | ⏳ Week 4+ | 월말 집계, 이미지 압축/삭제, 알림 발송 |
| `flutter-app` | ⏳ Week 2+ | 모바일 앱 (iOS/Android) |
| `docs/` | ✅ 본 문서 | 설계 + 작업 지시서 |

### 1.3 핵심 외부 의존

| 의존 | 용도 | 비용 | 확보 상태 |
|---|---|---|---|
| Claude API (Vision) | 영수증 OCR + 분류 | 영수증 1장 ₩20~30 (Sonnet 4.5) | 사용자가 console.anthropic.com에서 발급 필요 |
| FCM (Firebase) | 푸시 알림 (P1) | 무료 | 추후 v1.1에서 셋업 |
| Apple Developer | iOS TestFlight 배포 | $99/년 | 사용자 가입 필요 |
| kyuhyeong.com VPS | 호스팅 | 0원 (기존 활용) | ✅ 운영 중 |
| MariaDB | DB | 0원 (VPS 내) | ✅ 설치됨 |

---

## 2. 엑셀 → 앱 매핑 / 카테고리

### 2.1 우리집_가계부.xlsx (가계부 본체)

| 엑셀 시트 | 앱 화면/기능 | 우선순위 |
|---|---|---|
| 대시보드 | 홈 화면 — 이번 달 요약, 잉여금, 순자산, 결혼 진행률 카드 | P0 |
| 설정 | 설정 화면 — 카테고리 예산 관리 (가구별) | P0 |
| 월별기록 | 거래 입력 화면 (일별 입력) + 월별/카테고리별 자동 집계 화면 | P0 |
| 결혼 일시 지출 | 결혼 프로젝트 화면 — 항목별 예산 vs 실제 진행률 | P1 |
| 순자산 | 순자산 화면 — 자산/부채 입력, 월별 추이 차트 | P1 |
| 사용법 | 온보딩 / 도움말 | P2 |

### 2.2 우리집_자산관리.xlsx (투자)

본 앱에 통합하지 않음. 이유:
- 갱신 주기 다름 (가계부 매일 vs 투자 월 1회)
- 사용자 다름 (가계부 부부 vs 투자 본인)
- 도메인 다름 (현금흐름 vs 자산평가)

대신 앱의 순자산 화면에서 **투자 자산 평가액만 외부 입력 필드로 받아옴**. 추후 v2에서 별도 모듈로 통합 검토.

### 2.3 카테고리 22개 (엑셀에서 이식, MVP 시드 데이터)

```
수입 (4):
  - 본인 월급, 아내 월급, 보너스/성과급, 기타 수입

고정지출 (5):
  - 주거, 통신, 보험, 구독, 부모님 용돈

변동지출 (8):
  - 식비, 외식/카페, 교통/주유, 문화/데이트,
    의류/미용, 의료/건강, 경조사, 기타 변동

투자/저축 (5):
  - 비상금, 청년미래적금, 본인 ISA, 아내 ISA, KB 직투
```

AI 분류는 변동지출 8개 중 하나로 매핑하는 케이스가 가장 많을 것. 각 가구는 자신의 카테고리를 추가/수정 가능 (가구별 격리, §6 참조).

---

## 3. 핵심 기능: 영수증 OCR + AI 분류

### 3.1 사용자 흐름

```
[1] 결제 후 영수증 사진 촬영 (Flutter 카메라)
       ↓
[2] 이미지 업로드 (multipart/form-data → /api/receipts)
       ↓
[3] 백엔드: 이미지를 Claude Vision API에 전달
       ↓
[4] Claude가 영수증 분석 → 구조화된 JSON 반환
   { "date": "2026-05-18", "merchant": "스타벅스 강남점",
     "category": "외식/카페", "items": [...], "total": 8500,
     "confidence": 0.95 }
       ↓
[5] DB에 거래 레코드(DRAFT) + 디스크에 원본 이미지 저장
       ↓
[6] 앱에 결과 표시 → 사용자가 수정/확정 (1탭 컨펌)
       ↓
[7] 가구 내 다른 멤버에게 실시간 알림 (FCM, v1.1)
```

### 3.2 분류 정확도 향상 전략

1. **가맹점 학습 테이블 (가구별)**: `merchant_history` 테이블에 가구별 분류 이력을 누적. 프롬프트 컨텍스트로 주입하여 같은 가맹점은 일관 분류.

2. **신뢰도 기반 처리**:
   - `confidence ≥ 0.8`: 자동 확정 (DRAFT → CONFIRMED)
   - `0.5 ~ 0.8`: 사용자 컨펌 요청 (기본값 제시)
   - `< 0.5`: 사용자가 수동 카테고리 선택

3. **수정 피드백 루프**: 사용자가 AI 분류를 수정하면 `merchant_history` UPSERT. 시간이 지날수록 정확도 자동 향상.

4. **카드 명세서 일괄 처리** (P2): 월말에 카드사 PDF 명세서 업로드 → Claude가 한 번에 100건씩 분류 → 영수증 없는 항목 보완.

### 3.3 모델 선택 전략

- **MVP 기본값**: `claude-sonnet-4-5` (정확도 우선, 영수증당 약 ₩20~30)
- **운영 안정화 후**: `claude-haiku-4-5`로 다운그레이드 검토 (영수증당 약 ₩5~10, 4~5배 저렴)
- **A/B 검증**: 첫 1~2개월간 동일 영수증을 두 모델로 모두 분류 → 정확도 차이 측정 후 결정

### 3.4 프롬프트 관리

- 위치: `account-ai/src/main/resources/prompts/receipt-analysis.txt`
- 자리표시자: `{{MERCHANT_HISTORY}}` — 가구별 가맹점 학습 이력을 런타임 주입
- 22개 카테고리 후보 + 한국 가맹점 분류 가이드 포함
- 응답은 순수 JSON (코드 펜스 금지) 강제. 단 모델이 가끔 어기므로 `ReceiptAnalysisService.extractJson()` 에서 방어적으로 처리.

---

## 4. 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│  Flutter App (iOS/Android)                                  │
│  - 카메라 / 거래 입력 / 대시보드 / 푸시 알림                  │
└──────────────────┬──────────────────────────────────────────┘
                   │ HTTPS + JWT (household_id 클레임)
┌──────────────────▼──────────────────────────────────────────┐
│  nginx (account.kyuhyeong.com) — reverse proxy + SSL        │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│  Spring Boot 3.3+ (Gradle multi-module, Java 21)            │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ account-api    REST + JWT + HouseholdContext 진입   │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │ account-core   Entity, Repository, Service          │    │
│  │                Hibernate @Filter (가구 격리 본체)     │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │ account-ai     Claude Vision 통합 (RestClient)       │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │ account-batch  월말 집계, 이미지 정리, 알림 발송      │    │
│  └─────────────────────────────────────────────────────┘    │
└────────┬─────────────────────┬──────────────────┬───────────┘
         │                     │                  │
    ┌────▼────┐           ┌────▼────┐        ┌────▼────┐
    │ MariaDB │           │ Claude  │        │  FCM    │
    │ (Docker)│           │   API   │        │ (Push)  │
    └─────────┘           └─────────┘        └─────────┘
         │
    ┌────▼─────────┐
    │ /mnt/data/   │  영수증 이미지 (서버 디스크, 가구별 격리)
    │ receipts/    │  /receipts/{household_id}/{yyyy}/{mm}/...
    │   {hid}/...  │  Spring이 JWT 검증 후 stream으로 응답
    └──────────────┘
```

### 핵심 설계 원칙

1. **단일 VPS에서 모두 호스팅** (기존 kyuhyeong.com 활용, 신규 비용 0)
2. **Docker Compose 단일 stack**: Spring Boot + MariaDB + nginx
3. **이미지는 가구별 디렉토리 격리** (S3 안 씀, 2~20인 사용에 용량 충분)
4. **모든 도메인 데이터는 `household_id`로 격리** (Hibernate `@Filter` 자동 적용)
5. **외부 의존은 Claude API + FCM 두 개뿐** (서드파티 결제·OCR 안 씀)

---

## 5. 기술 스택

### 5.1 프론트엔드 (Flutter)

| 영역 | 라이브러리 | 용도 |
|---|---|---|
| 상태 관리 | `flutter_riverpod` | 전역 상태 (MyStar 패턴) |
| 라우팅 | `go_router` | 선언형 라우팅, 딥링크 |
| HTTP | `dio` + `retrofit` | API 클라이언트 (코드 생성) |
| 카메라 | `image_picker` | 영수증 촬영 |
| 이미지 처리 | `image` | 업로드 전 1280px 압축 |
| 차트 | `fl_chart` | 대시보드 |
| 로컬 캐시 | `drift` (SQLite) | 오프라인 모드, 동기화 큐 |
| 푸시 | `firebase_messaging` | FCM |
| 인증 저장 | `flutter_secure_storage` | JWT refresh token |
| 폼/입력 | `reactive_forms` | 거래 입력 폼 |
| 날짜 | `intl` | 한국어 로케일 |
| 생체 인증 | `local_auth` | Face ID / 지문 |

### 5.2 백엔드 (Spring Boot)

| 영역 | 기술 | 비고 |
|---|---|---|
| 언어 | **Java 21** (LTS) | 가상 스레드(Loom) 활용 — 영수증 I/O 대기에 적합 |
| 프레임워크 | Spring Boot 3.3+ | |
| 빌드 | Gradle 멀티 모듈 (Kotlin DSL) | ITSM toy 패턴 |
| ORM | Spring Data JPA + QueryDSL | 통계 쿼리 대비 |
| Multi-tenancy | Hibernate `@Filter` + `HouseholdContext` (ThreadLocal) | §6.2 참조 |
| DB | MariaDB 11.x | 기존 VPS 설치됨 |
| 마이그레이션 | **Flyway** | DB 스키마 버전 관리 |
| 인증 | Spring Security + JWT | HttpOnly 쿠키, `household_id` 클레임 (ITSM 패턴) |
| 검증 | Bean Validation | DTO 입력 검증 |
| AI 클라이언트 | **Spring `RestClient`** (직접 호출) | Anthropic Java SDK는 추후 검토. 가상 스레드 호환 |
| 이미지 처리 | `thumbnailator` 또는 `imgscalr` | 썸네일/압축 |
| 푸시 | Firebase Admin SDK (Java) | FCM (P1) |
| 배치 | Spring Batch | 월말 집계, 이미지 정리 |
| 변경 이력 | Hibernate Envers 또는 직접 구현 | `transaction_history` |
| 로깅 | Logback + JSON layout | nginx 로그와 통합 |
| 테스트 | JUnit 5 + Testcontainers + AssertJ | DB 통합 테스트 |

### 5.3 인프라

| 영역 | 도구 |
|---|---|
| 호스팅 | kyuhyeong.com VPS (Cafe24, 4GB) — 기존 활용 |
| 컨테이너 | Docker + Docker Compose |
| 리버스 프록시 | nginx (기존 셋업) |
| SSL | Let's Encrypt (certbot, 기존) |
| 도메인 | `account.kyuhyeong.com` (서브도메인 신규) |
| CI/CD | GitHub Actions (KH Shop 패턴) |
| 모바일 배포 | **TestFlight** (iOS, Apple Developer $99/년) + **Android APK Internal Track** |
| 시크릿 관리 | `application-secret.yml` 분리 + 환경변수 (KH Shop 패턴) |

---

## 6. 데이터 모델 (Multi-tenant ER)

### 6.1 전체 ER

```
─────────────────────────────────────────────────
[가구 / 멤버십] - 모든 데이터의 격리 단위
─────────────────────────────────────────────────

users
  id, email, password_hash, name, fcm_token,
  created_at, last_login_at

households
  id, name, plan_type(PERSONAL|FAMILY|PRO),  -- 추후 티어 확장
  owner_user_id (FK→users), data_retention_months,
  max_members, created_at

household_members
  id, household_id (FK), user_id (FK),
  role(OWNER|MEMBER), invited_by (FK→users, nullable),
  joined_at
  UNIQUE (household_id, user_id)

─────────────────────────────────────────────────
[도메인 데이터] - 모두 household_id로 격리
─────────────────────────────────────────────────

categories
  id, household_id (FK), name,
  type(INCOME|FIXED|VARIABLE|INVEST),
  budget_monthly, sort_order, created_at
  INDEX (household_id, sort_order)

transactions
  id, household_id (FK), user_id (FK, 입력자),
  category_id (FK), amount, occurred_at,
  merchant, payment_method, memo,
  receipt_id (FK, nullable), confidence,
  status(DRAFT|CONFIRMED),
  created_at, updated_at, updated_by_user_id (FK)
  INDEX (household_id, occurred_at)
  INDEX (household_id, category_id, occurred_at)

transaction_history  -- 변경 이력 (감사 로그)
  id, transaction_id (FK), household_id (FK),
  changed_by_user_id (FK), changed_at,
  change_type(CREATE|UPDATE|DELETE),
  before_json (TEXT), after_json (TEXT)
  INDEX (transaction_id, changed_at)

receipts
  id, household_id (FK), user_id (FK, 업로더),
  image_path,  -- /mnt/data/receipts/{hid}/{yyyy}/{mm}/{uuid}.jpg
  original_filename, file_size,
  ocr_raw_json (TEXT),  -- Claude 원본 응답 보관
  processed_at, created_at
  INDEX (household_id, created_at)

merchant_history  -- 가구별 가맹점 학습
  id, household_id (FK), merchant_name, category_id (FK),
  count, last_used_at
  UNIQUE (household_id, merchant_name)

monthly_summaries  -- 배치로 사전 계산
  id, household_id (FK), year_month,
  category_id (FK), total_amount, transaction_count
  UNIQUE (household_id, year_month, category_id)

assets / liabilities  -- 순자산용 (v1.1)
  id, household_id (FK), name, type, balance,
  recorded_at (YYYY-MM-01 단위)
  INDEX (household_id, recorded_at)

wedding_items  -- 결혼 일시 지출 (v1.1, 해당 가구만)
  id, household_id (FK), section, name, budget,
  actual, parent_support, memo, paid_at
```

### 6.2 가구 격리 메커니즘 (4단계)

1. **JWT 클레임**: 로그인 시 `household_id` 포함 (사용자가 여러 가구 소속이면 활성 가구 선택)
2. **Spring 진입점**: 모든 컨트롤러 호출 시 `HouseholdContext` (ThreadLocal)에 주입. `OncePerRequestFilter` 사용, 응답 후 clear.
3. **Hibernate `@Filter`**: 모든 도메인 엔티티에 `@Filter("householdFilter", condition = "household_id = :current")` 활성화
4. **Repository 강제**: `findByHouseholdIdAnd*` 메서드만 노출, raw query 금지

### 6.3 MVP 운영 (부부 단계 시드)

- `households` row 1개: `(id=1, name='우리집', plan_type='PERSONAL')`
- `household_members` row 2개: 본인 `OWNER`, 아내 `MEMBER`
- 회원가입/초대 화면 없음 (v1.5)
- 단, **컬럼·인덱스·필터링 로직은 처음부터 작동** → v1.5에서 초대 화면만 얹으면 끝

---

## 7. API 설계 / 인증 / 보안

### 7.1 엔드포인트 요약

모든 인증 API는 JWT에서 `household_id`를 추출하여 자동 격리. 명시적 path param 없음.

```
POST   /api/auth/login                   # 로그인 (JWT 발급, household_id 클레임)
POST   /api/auth/refresh                 # 토큰 갱신
GET    /api/auth/me                      # 본인 정보 + 소속 가구 목록
POST   /api/auth/switch-household        # 가구 전환 (여러 가구 소속 시, v1.5)

POST   /api/receipts                     # 영수증 업로드 (multipart)
                                           → AI 분석 후 DRAFT 거래 생성
GET    /api/receipts/{id}                # 영수증 + 분석 결과 조회
GET    /api/receipts/{id}/image          # 이미지 stream (JWT 검증 후)

POST   /api/transactions                 # 수동 거래 입력
PUT    /api/transactions/{id}            # 거래 수정 (카테고리 변경 등)
                                           → merchant_history 학습 + history 적재
DELETE /api/transactions/{id}            # soft delete
GET    /api/transactions                 # 필터/페이징
GET    /api/transactions/since/{ts}      # 동기화용 (가구 내 변경분)

GET    /api/dashboard/current-month      # 이번 달 요약 (홈 화면)
GET    /api/dashboard/networth           # 순자산 추이 (v1.1)
GET    /api/dashboard/wedding            # 결혼 진행률 (v1.1)

GET    /api/categories                   # 카테고리 + 예산 (가구별)
PUT    /api/categories/{id}/budget       # 예산 수정

POST   /api/assets, /api/liabilities     # 순자산 입력 (v1.1)
GET    /api/networth/history?months=12

POST   /api/wedding-items                # (v1.1)
PUT    /api/wedding-items/{id}
GET    /api/wedding-items

# v1.5 (가구 확장 시)
POST   /api/households                   # 가구 생성
POST   /api/households/{id}/invite       # 멤버 초대 (이메일)
DELETE /api/households/{id}/members/{userId}
```

### 7.2 실시간 동기화 전략

가구 내 한 명이 거래를 추가/수정하면 다른 멤버 단말에도 반영되어야 함.

- **풀링 (MVP)**: 앱이 30~60초마다 `/api/transactions/since/{timestamp}` 호출. 배터리/네트워크 부담 적음. 2~20인 가구에 충분.
- **FCM Silent Push (v1.1)**: 거래 발생 시 가구 내 다른 멤버 단말에 silent push → 앱이 재조회. 즉시성 ↑.

### 7.3 인증 흐름

1. 이메일 + 비밀번호 로그인 → JWT 발급 (access 15분, refresh 30일)
2. JWT 클레임: `user_id`, `household_id`(현재 활성), `role`
3. Access token은 메모리, refresh token은 `flutter_secure_storage`
4. 자동 로그인 (앱 실행 시 refresh로 access 갱신)
5. 여러 가구 소속 시 `/api/auth/switch-household` → 새 JWT 재발급 (v1.5)

### 7.4 보안 추가 조치

- **회원가입 외부 차단** (MVP): 가입 화면 없음, DB 시드로 2명 등록. `robots.txt` + `X-Robots-Tag: noindex`로 검색엔진 노출 차단
- **앱 진입 시 생체 인증** (`local_auth`)
- **Rate Limiting**: nginx + Spring 양쪽에서 IP/사용자별
- **이미지 접근 제어**: 영수증은 JWT 검증 후 Spring stream (nginx 직접 노출 X)
- **CORS**: 모바일 앱만 허용 (웹 클라이언트 없음)
- **HTTPS 강제**: HSTS, http → https 리다이렉트
- **백업**: MariaDB 일일 덤프 (cron) + 영수증 이미지 주 1회 Cloudflare R2 (무료 10GB)

### 7.5 영수증 보관 정책

- **업로드 시**: 즉시 1280px 이하로 리사이즈 + JPEG 80% (1장 약 200KB)
- **1년 경과**: 추가 압축 800px / JPEG 60% (1장 약 80KB) — Spring Batch 월 1회 잡
- **5년 경과**: 자동 삭제 — DB 거래 레코드와 `ocr_raw_json`은 유지
- **가구별 정책**: `households.data_retention_months` 컬럼으로 가구마다 다르게 적용 가능

---

## 8. ✅ 다음 작업: Week 1

> **현재 페이즈**. 에이전트는 이 절의 작업만 우선 진행. 완료 후 §9의 다음 페이즈로 넘어가기 전 사용자 확인.

### 8.1 현재 상태 점검

**이미 존재**:
- ✅ Repo: <https://github.com/LeeKyuHyeong/account-app> (Public, 초기 커밋만)
- ✅ `docs/account.md` (본 문서)
- ✅ `account-ai/` 프로토타입 (Claude Vision 통합, RestClient 기반, 단위 테스트 6개)
- ✅ `.gitignore` (시크릿 사전 차단 패턴 포함)
- ✅ `README.md` (최상위)

**없음 (Week 1에 생성)**:
- ❌ Gradle 멀티 모듈 루트 (`settings.gradle.kts`, 루트 `build.gradle.kts`)
- ❌ `account-core` 모듈 (Entity, Repository, Multi-tenant 격리 본체)
- ❌ `account-api` 모듈 (REST, JWT, HouseholdContext)
- ❌ Flyway 마이그레이션 (`V1__init_schema.sql`, `V2__seed.sql`)
- ❌ `docker-compose.yml` (MariaDB 컨테이너)
- ❌ 격리 검증 통합 테스트 (Testcontainers)

### 8.2 Week 1 작업 순서 (체크리스트)

작업은 순서대로 진행. 각 작업 완료 시 커밋 + 다음 작업.

#### Task 1. Gradle 멀티 모듈 루트 셋업 (반나절)

**목표**: 빈 모듈 3개(`account-core`, `account-api`, `account-batch`) + 기존 `account-ai` 통합. `./gradlew :account-api:bootRun` 가능 상태.

- [ ] 루트 `settings.gradle.kts` 작성
  ```kotlin
  rootProject.name = "account-app"
  include("account-core", "account-api", "account-ai", "account-batch")
  ```
- [ ] 루트 `build.gradle.kts` 작성 (Java 21 toolchain, Spring Boot 의존성 관리, 공통 의존성)
- [ ] 각 모듈 `build.gradle.kts` 작성 (의존성 그래프: api → core, batch → core, ai 독립)
- [ ] `gradle/wrapper/` (Gradle Wrapper) 추가 — `gradle wrapper --gradle-version 8.10`
- [ ] `account-ai` 모듈의 기존 build.gradle.kts를 멀티 모듈에 편입 (standalone bootRun 제거, 의존성 정리)
- [ ] 빌드 확인: `./gradlew build` 성공
- [ ] Acceptance: 빈 `AccountApiApplication.java` 작성하고 `./gradlew :account-api:bootRun` 정상 기동 (DB 연결 전 단계라 에러 OK, "Started AccountApiApplication" 로그만 확인)

**커밋 메시지**: `feat(build): setup gradle multi-module structure`

#### Task 2. MariaDB Docker + Flyway 스키마 (반나절)

**목표**: MariaDB 컨테이너 + Flyway가 부팅 시 자동으로 스키마 생성. 가구 2개 + 사용자 4명 시드 (격리 테스트용).

- [ ] 루트에 `docker-compose.yml` 작성 (MariaDB 11.x, 포트 3306, volume `./data/mariadb`)
- [ ] `account-core/src/main/resources/db/migration/V1__init_schema.sql`
  - §6.1의 모든 테이블 (households, household_members, users, categories, transactions, transaction_history, receipts, merchant_history, monthly_summaries, assets, liabilities, wedding_items)
  - 인덱스 §6.1 명시대로
  - 외래키는 명시적으로 (CASCADE 정책은 안전 우선: ON DELETE RESTRICT 기본)
- [ ] `V2__seed_dev.sql` — 개발 환경용 시드:
  - households 2개 (`우리집`, `테스트가구`) — 격리 검증용
  - users 4명, household_members 4건
  - categories: 우리집 22개(§2.3), 테스트가구 5개 (격리 검증용으로 다르게)
- [ ] `application.yml` 에 Flyway + JPA 설정
- [ ] 부팅 후 `SHOW TABLES;` 로 13개 테이블 확인
- [ ] Acceptance: `./gradlew :account-api:bootRun` 시 Flyway가 V1, V2 자동 적용. 두 가구 데이터 시드 확인.

**커밋 메시지**: `feat(core): add flyway migrations with seed data for two households`

#### Task 3. JPA Entity + Repository (1일)

**목표**: §6.1의 모든 테이블에 대응하는 Entity와 기본 Repository. 단 본 작업에서 Hibernate `@Filter`는 아직 활성화 X (Task 4에서 활성화).

- [ ] `account-core/src/main/java/com/kyuhyeong/account/core/entity/` 하위에 Entity 작성:
  - `User`, `Household`, `HouseholdMember`, `Category`, `Transaction`, `TransactionHistory`, `Receipt`, `MerchantHistory`, `MonthlySummary`, `Asset`, `Liability`, `WeddingItem`
- [ ] 모든 도메인 Entity(households, household_members 제외)에 `household_id` 필드 + `@ManyToOne(fetch = LAZY)`
- [ ] enum: `CategoryType`, `TransactionStatus`, `HouseholdRole`, `PlanType`, `ChangeType`
- [ ] Lombok: `@Getter`, `@NoArgsConstructor(access=PROTECTED)`, `@AllArgsConstructor(access=PRIVATE)`, `@Builder` (Setter는 절대 X — §10.1 참조)
- [ ] Repository 인터페이스 — Spring Data JPA `JpaRepository` 상속, raw query 메서드 일체 추가 금지 (§10.5)
- [ ] Entity ↔ DTO 변환은 일단 Entity 직접 사용. Mapping 레이어는 Task 6 (Controller 작업) 때 추가.

**커밋 메시지**: `feat(core): add JPA entities and repositories for all domain tables`

#### Task 4. HouseholdContext + Hibernate Filter (1일) — 본 페이즈 핵심

**목표**: 두 가구 데이터가 같은 DB에 있을 때, 가구#1로 인증된 요청이 가구#2 데이터를 절대 못 보게 한다. 통합 테스트로 강제 검증.

- [ ] `account-core/.../tenant/HouseholdContext.java`
  ```java
  public final class HouseholdContext {
      private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();
      public static void set(Long householdId) { CURRENT.set(householdId); }
      public static Long get() {
          Long id = CURRENT.get();
          if (id == null) throw new IllegalStateException("HouseholdContext not set");
          return id;
      }
      public static void clear() { CURRENT.remove(); }
  }
  ```
- [ ] 모든 도메인 Entity에 `@FilterDef` + `@Filter` 추가
  ```java
  @FilterDef(name = "householdFilter",
             parameters = @ParamDef(name = "currentHouseholdId", type = Long.class))
  @Filter(name = "householdFilter",
          condition = "household_id = :currentHouseholdId")
  ```
- [ ] `HouseholdFilterAspect` 또는 `EntityManager` 주입 후 매 요청마다 필터 활성화 (Spring AOP 또는 `OncePerRequestFilter` 내에서)
- [ ] `account-api/.../filter/HouseholdContextFilter.java` — Servlet Filter 또는 Spring Filter:
  - JWT 파싱 후 `household_id` 추출 (Task 5에서 JWT 도입 전이라 일시적으로 헤더 `X-Household-Id` 사용. Task 5에서 JWT로 교체)
  - `HouseholdContext.set(id)` + `entityManager.unwrap(Session.class).enableFilter(...).setParameter(...)`
  - finally 블록에서 `HouseholdContext.clear()`
- [ ] **격리 검증 통합 테스트** (`account-api/src/test/...`, Testcontainers + MariaDB):
  - 가구#1, 가구#2 각각 시드
  - `X-Household-Id: 1` 헤더로 `GET /api/categories` 호출 → 가구#1 카테고리 22개만 반환되는지
  - `X-Household-Id: 2` 헤더로 동일 호출 → 가구#2 카테고리 5개만 반환되는지
  - 두 결과의 ID 집합이 disjoint 한지 (교집합 0)
- [ ] Acceptance: 위 통합 테스트 통과. **이 테스트는 본 프로젝트에서 가장 중요한 보안 검증**이라 절대 빠뜨리지 말 것.

**커밋 메시지**: `feat(core): add multi-tenant isolation via HouseholdContext + Hibernate filter`

#### Task 5. JWT 인증 셋업 (1일)

**목표**: ITSM toy 프로젝트의 JWT 패턴 재활용. 로그인 → JWT 발급 → 헤더 검증 → `HouseholdContext` 주입까지의 흐름 완성.

- [ ] `account-api/.../security/JwtTokenProvider.java` — access/refresh 발급, 검증, 클레임 추출
  - 클레임: `sub`(user_id), `household_id`, `role`
  - access 15분 / refresh 30일
  - secret은 `application-secret.yml` 분리 (`account.jwt.secret`)
- [ ] `JwtAuthenticationFilter` (Spring Security `OncePerRequestFilter`):
  - Authorization 헤더 또는 HttpOnly 쿠키에서 JWT 추출
  - 검증 → `HouseholdContext.set(household_id)`
  - finally clear
- [ ] `SecurityConfig` — `/api/auth/**` 외 인증 필수
- [ ] `AuthController`:
  - `POST /api/auth/login` — 이메일 + 비밀번호 검증 (BCrypt), 사용자가 여러 가구 소속이면 활성 가구 결정 (지금은 첫 번째 가구 자동 선택)
  - `POST /api/auth/refresh`
  - `GET /api/auth/me` — 현재 사용자 + 소속 가구 목록
- [ ] Task 4의 임시 `X-Household-Id` 헤더 처리 제거 — JWT 클레임에서 추출하도록 교체
- [ ] Task 4의 격리 검증 테스트를 JWT 기반으로 수정 (로그인 → 토큰 받기 → API 호출)
- [ ] Acceptance: 사용자#1 토큰으로 가구#2 자원 접근 시 빈 결과 또는 404.

**커밋 메시지**: `feat(api): add JWT authentication with household_id claim`

#### Task 6. account-ai 모듈 멀티모듈 통합 (반나절)

**목표**: 기존 `account-ai` 프로토타입의 `MerchantHistoryProvider`를 `account-core`의 JPA 구현체로 연결. `ReceiptController`를 `account-api`로 이전 (`@RestController` 진입점은 api 모듈에 위치).

- [ ] `account-core`에 `JpaMerchantHistoryProvider implements MerchantHistoryProvider` 추가
  ```java
  @Service
  public class JpaMerchantHistoryProvider implements MerchantHistoryProvider {
      private final MerchantHistoryRepository repo;
      @Override
      public MerchantHistoryContext getRecentHistory(Long householdId, int maxEntries) {
          return new MerchantHistoryContext(
              householdId,
              repo.findTopByHouseholdIdOrderByLastUsedAtDesc(
                      householdId, PageRequest.of(0, maxEntries))
                  .stream()
                  .map(r -> new MerchantHistoryContext.Entry(
                      r.getMerchantName(), r.getCategory().getName(),
                      r.getCount(), r.getLastUsedAt()))
                  .toList()
          );
      }
  }
  ```
- [ ] `account-ai`에서 `ReceiptController` 제거 → `account-api`에 동일 컨트롤러 이전. JWT에서 `household_id` 추출하도록 `X-Household-Id` 헤더 처리 제거.
- [ ] `account-api/build.gradle.kts`에 `implementation(project(":account-ai"))` 추가
- [ ] 영수증 업로드 → DRAFT 거래 자동 생성 흐름 추가 (Task 2의 transactions 테이블에 적재)
- [ ] 이미지 저장 위치: `/mnt/data/receipts/{household_id}/{yyyy}/{mm}/{uuid}.jpg` (개발 환경은 `./data/receipts/...`)
- [ ] 통합 테스트: 영수증 업로드 → DRAFT 거래 생성 → 본인 가구로만 조회됨
- [ ] Acceptance: `curl -X POST .../api/receipts -F "image=@..."` 로 실제 분석 + DB 저장까지 동작 (Claude API 키 있는 환경에서)

**커밋 메시지**: `feat(api): integrate account-ai with multi-module structure`

### 8.3 Week 1 완료 기준

위 6개 Task가 모두 완료되고 다음이 보장될 때 Week 1 종료:

1. ✅ `./gradlew build` 성공 (모든 모듈)
2. ✅ `docker-compose up -d` 로 MariaDB 기동 + Flyway 자동 마이그레이션 적용
3. ✅ `./gradlew :account-api:bootRun` 정상 기동, `/api/auth/login` 호출 가능
4. ✅ **격리 검증 통합 테스트 통과** (가장 중요)
5. ✅ `curl` 로 영수증 업로드 → Claude 분석 → DRAFT 거래 생성 → 본인 가구로만 조회됨
6. ✅ 시크릿이 한 줄도 커밋되지 않음 (§10.2 확인)

Week 1 완료 시 사용자에게 확인 요청 → 승인 후 Week 2 (Flutter 셋업) 진행.

---

## 9. 개발 로드맵 (Week 2-6 + 이후)

### Week 2-3: Flutter 셋업 + 거래 입력 화면

- [ ] `flutter-app` 모듈 추가 (`flutter create flutter_app`)
- [ ] Riverpod + go_router + dio 셋업
- [ ] 로그인 화면 + JWT 토큰 자동 갱신
- [ ] 거래 목록 화면 + 필터
- [ ] 수동 거래 입력 폼

### Week 4: 카메라 + 영수증 촬영

- [ ] `image_picker` 통합
- [ ] 클라이언트 측 1280px 압축 (`image` 라이브러리)
- [ ] 업로드 → 분석 결과 화면 → 컨펌 흐름
- [ ] 신뢰도별 UI 분기 (자동 확정 / 컨펌 / 수동 분류)

### Week 5: 학습 + 대시보드

- [ ] `merchant_history` 학습 피드백 루프 (사용자 수정 시 UPSERT)
- [ ] 홈 화면 — 이번 달 카드 (수입/지출/잉여금)
- [ ] 카메라 FAB + 앱 아이콘 Quick Action
- [ ] 월별 집계 API + `MonthlySummary` 사전 계산 배치
- [ ] 카테고리별 추이 차트 (`fl_chart`)

### Week 6: 배포

- [ ] `account.kyuhyeong.com` 서브도메인 추가 (nginx + Let's Encrypt)
- [ ] Docker Compose 운영 stack 구성
- [ ] GitHub Actions CI/CD (KH Shop 패턴)
- [ ] TestFlight 빌드 + 부부 단말 설치
- [ ] Android APK Internal Track

### v1.1 (MVP 후 점진)

- 순자산 화면 (자산/부채 + 추이)
- 결혼 일시 지출 화면
- FCM 푸시 (silent push 동기화 + 알림)
- 예산 초과 경고
- 영수증 압축/삭제 배치 잡

### v1.5 (가구 확장 시)

- 가구 초대 플로우 (이메일)
- OWNER/MEMBER 역할 분리 강화 (예산 수정은 OWNER만)
- 회원가입 화면
- 가구별 카테고리 커스터마이징 UI
- 탈퇴/데이터 삭제 자동 배치

### v2 (장기 — 사업화 검토 단계)

- 카드 PDF 명세서 일괄 분류
- 음성 입력 거래
- 자산관리 엑셀 통합 (투자 모듈)
- 연말정산 시뮬레이터
- 구독 티어 (PERSONAL / FAMILY / PRO) — In-App Purchase
- 개인정보처리방침 본격 정비

---

## 10. 작업 규칙

### 10.1 코드 스타일

- **Lombok 사용 OK**. `@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access=PROTECTED)`, `@AllArgsConstructor(access=PRIVATE)` 까지.
- **`@Setter` 절대 금지**. Entity는 비즈니스 메서드를 통해서만 상태 변경. DTO는 record 또는 final 필드.
- **DTO는 Java 21 record 우선**. 여러 표현이 필요한 경우만 class.
- **Service 메서드는 명사형 X, 동사형 O**. `getCategoryList()` 보다 `findCategoriesByHousehold()`.
- **Optional 반환은 Repository까지만**. Service 이상에서는 도메인 예외 throw.
- **null 금지**. 컬렉션은 빈 컬렉션, 단일 객체는 Optional 또는 예외.
- **Java 21 패턴 적극 활용**: record, sealed, switch expression, pattern matching.
- **가상 스레드 활성화**: `application.yml`에 `spring.threads.virtual.enabled: true`

### 10.2 시크릿 관리

**절대 커밋하지 말 것**:
- `application-secret.yml`, `application-secret.yaml`, `application-secret.properties`
- `application-local.yml`, `application-local.yaml`
- `.env`, `.env.*`
- `*.pem`, `*.key`, `id_rsa*`
- Claude API 키 (`sk-ant-...`), JWT secret, DB 비밀번호

**커밋 전 검증**:
```bash
git diff --cached | grep -iE "sk-ant|password.*=.*[a-zA-Z0-9]{8}|secret.*=.*[a-zA-Z0-9]{16}"
# 결과가 비어있어야 정상
```

**환경변수 패턴** (KH Shop 사고 재발 방지):
```yaml
# application.yml
account:
  claude:
    api-key: ${ACCOUNT_CLAUDE_API_KEY}
  jwt:
    secret: ${ACCOUNT_JWT_SECRET}
  datasource:
    password: ${ACCOUNT_DB_PASSWORD}
```

### 10.3 커밋 메시지 컨벤션

Conventional Commits 형식:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

- **type**: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `build`, `ci`
- **scope**: `core`, `api`, `ai`, `batch`, `flutter`, `build`, `infra`
- **예시**:
  - `feat(core): add Household and HouseholdMember entities`
  - `feat(api): integrate JwtAuthenticationFilter with HouseholdContext`
  - `test(core): add tenant isolation integration test`
  - `fix(ai): handle code-fenced JSON response from Claude`
  - `docs: update Week 1 task progress`

### 10.4 테스트 정책

- **격리 검증 테스트는 필수**. §8.2 Task 4의 통합 테스트는 절대 빠뜨리지 말 것.
- **단위 테스트는 핵심 비즈니스 로직만**. Getter/Setter 같은 trivial 테스트는 X.
- **DB 통합 테스트는 Testcontainers**. H2 등 인메모리 DB로 대체 X (방언 차이).
- **테스트 격리**: 각 테스트는 자체 데이터 시드. 다른 테스트의 부산물에 의존 X.
- **AssertJ 사용**. JUnit assertions 보다 가독성 ↑.

### 10.5 의존성 / 데이터 접근 규칙

- **`account-ai`는 `account-core`에 의존하지 않음** (인터페이스만 의존: `MerchantHistoryProvider`). 단방향 결합도 유지.
- **`account-api`는 `account-core` + `account-ai`에 의존**.
- **`account-batch`는 `account-core`에 의존**, `account-api` 의존 금지.
- **Repository에 raw SQL 메서드 추가 금지**. 메서드 이름 규칙 또는 QueryDSL 사용. `@Query` 어노테이션 사용 시 사용자에게 사유 보고.
- **`household_id` 없는 메서드 정의 금지**. 모든 Repository 조회 메서드는 `findByHouseholdIdAnd*` 형태.

### 10.6 모르는 것 처리

- 외부 의존(IP, 도메인, API 키, 계정 정보)이 명확하지 않으면 **추측 금지**. 사용자에게 명시적 질문.
- §11 결정 사항 변경이 필요해 보이면 **임의 변경 금지**. 사용자에게 사유와 영향 보고 후 승인.
- 새 라이브러리 도입 시 **이유 + 대안 비교**를 커밋 메시지나 PR 본문에 명시.

---

## 11. 확정된 결정 사항 (7개)

| # | 항목 | 결정 | 변경 시 영향 |
|---|---|---|---|
| 1 | Java 버전 | **Java 21** + 가상 스레드 | toolchain 전체 재설정 |
| 2 | iOS 배포 | **TestFlight ($99/년)** | Apple Developer 가입 |
| 3 | Claude API | **별도 키 발급** + Console 한도 설정 ($10/월) | Anthropic 결제 카드 등록 |
| 4 | 영수증 보관 | **5년 + 단계적 압축 + 가구별 정책** | `households.data_retention_months` 활용 |
| 5 | 거래 권한 | **가구 멤버 모두 수정 + 변경 이력 로그** | `transaction_history` 자동 적재 |
| 6 | 첫 화면 | **홈 + 카메라 FAB + 앱 아이콘 Quick Action** | Flutter UX 패턴 |
| 7 | Multi-tenant | **모든 도메인 테이블 `household_id` + Hibernate Filter + JWT 클레임** | 본 프로젝트 가장 중요한 결정 — 변경 시 전면 재작업 |

**MVP에서 의도적으로 제외**:
- 회원가입/초대 화면 (v1.5)
- OWNER/MEMBER 역할 차등 (v1.5)
- 가구별 카테고리 커스터마이징 UI (v1.5)
- FCM 푸시 (Week 6은 풀링)
- 결혼 일시 지출 / 순자산 화면 (v1.1)

---

## 12. 비용 추정

| 항목 | 부부 단계 | 20명 확장 단계 |
|---|---|---|
| VPS | 0원 (기존 kyuhyeong.com 활용) | 0원 (4GB로 충분) |
| 도메인 | 0원 (서브도메인) | 0원 |
| Claude API | 약 ₩5,000/월 (영수증 200건 × Sonnet 4.5) | 약 ₩50,000/월 (10배) — Haiku 시 ₩10,000 |
| FCM | 0원 (무료 티어) | 0원 |
| Apple Developer | $99/년 ≈ 월 ₩11,000 | 동일 |
| 백업 스토리지 | 0원 (Cloudflare R2 무료 10GB) | 동일 |
| **합계** | **약 ₩16,000/월** | **약 ₩21,000~61,000/월** |

---

## 13. 확장성 / 사업화 가능성

기술적으로는 multi-tenant 설계로 자연스럽게 확장 가능:

- **소규모 (20명 내외)**: 현재 VPS로 충분, 비용 거의 변화 없음
- **중규모 사업화 (수백~수천 가구)**:
  - 디스크: 외부 오브젝트 스토리지로 이전 (Cloudflare R2, AWS S3)
  - DB: read replica 또는 더 큰 인스턴스
  - 결제: Apple/Google In-App Purchase 통합 (수수료 15~30%)
  - 법적: 개인정보처리방침 게재, 개인정보보호책임자 지정
  - 운영: CS 채널, 모니터링/알람 강화

`plan_type` 컬럼이 이미 있어 티어 운영 가능 (PERSONAL / FAMILY / PRO). 단, **본격 사업화는 별도 결정 사항이며 본 문서의 범위가 아님**. 포트폴리오 관점에서는 "확장 가능한 멀티테넌트 아키텍처를 의식하고 설계한 프로젝트" 자체가 면접 어필 포인트.

---

## 14. 부록: 재활용 자산 / 환경 정보

### 14.1 본인 코드 재활용 가능 지점

- **ITSM toy 프로젝트** (현재 진행 중):
  - Gradle 멀티 모듈 구조 → §8.2 Task 1 참조
  - JWT + HttpOnly 쿠키 → §8.2 Task 5 참조
  - Spring Batch 9개 잡 → Week 4 배치 잡 참조
  - 환경 변수 분리 패턴

- **KH Shop**:
  - `application-secret.properties` 분리 패턴 → §10.2
  - GitHub Actions CI/CD → Week 6 배포
  - OAuth2 (Google/Kakao/Naver) → v1.5에서 활용 가능

- **MyStar Flutter 앱**:
  - Flutter 프로젝트 구조
  - Riverpod 패턴
  - go_router 라우팅

- **MCP 서버 (YouTube)**:
  - Claude API 클라이언트 패턴 (RestClient 사용으로 변경됨)
  - JSON 파싱 / 에러 처리

- **kyuhyeong.com 모니터링 대시보드**:
  - Spring Actuator → 운영 모니터링 통합

- **AssetInsight-Monorepo** (자산 스냅샷, 별도 보존):
  - 직접 코드 재활용은 X (컨셉 다름)
  - 단, OAuth 통합 / GitHub Actions 패턴 참고 가능

### 14.2 운영 환경 정보

- **VPS**: kyuhyeong.com, 175.125.21.245, Cafe24 4GB
- **OS**: CentOS / RHEL 계열
- **기존 서비스 도메인**: shop/game/itsm/api.kyuhyeong.com (각각 다른 Docker 컨테이너)
- **추가 예정**: account.kyuhyeong.com (Week 6)
- **MariaDB**: 11.x, 기존 인스턴스 활용 또는 별도 컨테이너 분리 (보안상 분리 권장)
- **nginx**: 이미 reverse proxy 셋업됨, server block 추가만 필요
- **SSL**: certbot 이미 셋업, 도메인 추가 시 자동 발급

### 14.3 .gitignore 표준 (이미 적용됨, 변경 시 주의)

`.gitignore`는 시크릿 사전 차단을 위한 안전장치. 임의로 제거하지 말 것. 특히 다음 패턴:

```gitignore
**/application-secret.yml
**/application-secret.yaml
**/application-secret.properties
**/application-local.yml
**/*.env
.env*
*.pem
*.key
**/receipts/    # 실제 영수증 이미지 (실명/실금액)
```

### 14.4 의문 사항 발생 시

본 문서로 해결되지 않는 의문이 생기면:

1. 본 문서 검색 (Ctrl+F) — 같은 키워드가 다른 절에 있을 수 있음
2. 그래도 모호하면 사용자에게 명시적 질문 (추측 금지)
3. 외부 정보가 필요하면: Spring/Hibernate/Flutter 공식 docs 우선, Stack Overflow 신중히 활용

---

*이 문서는 단순 설계도가 아니라 작업 지시서다. §8의 작업이 완료되면 본 문서의 "현재 페이즈" 표시(문서 상단)와 §8 자체를 업데이트하여 다음 페이즈로 진행한다.*
