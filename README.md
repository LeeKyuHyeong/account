# Account-App

부부/가구 단위 가계부 앱. 영수증 사진을 찍으면 Claude Vision API가 OCR + 카테고리 자동 분류 후 저장한다. Multi-tenant(가구 단위) 구조로 처음부터 설계되어 추후 가까운 인원(20명 내외)으로의 확장이 가능.

## 설계 / 작업 지시서

전체 설계와 현재 작업 페이즈는 [`docs/account.md`](docs/account.md) 참조.

> **에이전트(Claude Code 등)는 항상 `docs/account.md`의 §0(작업 가이드)과 §8(현재 작업)을 먼저 읽고 그 안의 작업만 수행한다.**

## 모노레포 구성

| 모듈 | 상태 | 책임 |
|---|---|---|
| `account-ai` | ✅ 프로토타입 존재 | Claude Vision API 통합, 영수증 OCR + 카테고리 분류 |
| `account-api` | ⏳ Week 1 | REST 엔드포인트, JWT 인증, 가구 격리 진입점 |
| `account-core` | ⏳ Week 1 | Entity, Repository, Service. Multi-tenant 격리 본체 |
| `account-batch` | ⏳ Week 4+ | 월말 집계, 이미지 정리, 알림 발송 |
| `flutter-app` | ⏳ Week 2+ | 모바일 앱 (iOS/Android) |
| `docs/` | ✅ 본 문서 | 설계 + 작업 지시서 |

## 기술 스택

**Backend**: Java 21 (가상 스레드) · Spring Boot 3.3+ · MariaDB 11.x · Hibernate `@Filter` 기반 multi-tenant
**Mobile**: Flutter · Riverpod · go_router · drift (오프라인 큐)
**AI**: Claude Vision API (Sonnet 4.5, 가구별 가맹점 학습)
**Infra**: kyuhyeong.com VPS · nginx · Docker Compose · Let's Encrypt
**CI/CD**: GitHub Actions

## 7개 핵심 결정 (요약)

| # | 항목 | 결정 |
|---|---|---|
| 1 | Java 버전 | Java 21 + 가상 스레드 |
| 2 | iOS 배포 | TestFlight ($99/년) |
| 3 | Claude API | 별도 키 + Console 한도 설정 |
| 4 | 영수증 보관 | 5년 + 단계적 압축 + 가구별 정책 |
| 5 | 거래 권한 | 가구 멤버 모두 수정 + 변경 이력 로그 |
| 6 | 첫 화면 | 홈 + 카메라 FAB + 앱 아이콘 Quick Action |
| 7 | Multi-tenant | 처음부터 `household_id` 기반 격리 |

상세는 `docs/account.md` §11 참조.

## 시크릿 관리

`application-secret.yml`, `.env`, `*.pem`, `*.key` 등은 `.gitignore`로 차단됨. 환경변수 또는 `application-secret.yml` 분리 사용. 자세한 내용은 `docs/account.md` §10.2 참조.

```yaml
# application.yml (커밋 OK)
account:
  claude:
    api-key: ${ACCOUNT_CLAUDE_API_KEY}   # 환경변수 주입
  jwt:
    secret: ${ACCOUNT_JWT_SECRET}
```

## 개발 시작

현재는 `account-ai` 모듈 단독 빌드만 가능. 멀티 모듈 루트(`settings.gradle.kts`, 루트 `build.gradle.kts`) 셋업은 Week 1 Task 1에서 진행.

Week 1 작업 순서는 `docs/account.md` §8.2 참조.
