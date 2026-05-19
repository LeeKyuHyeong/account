# account-ai 모듈 (영수증 OCR + AI 분류 프로토타입)

가계부 앱(Account)의 핵심 AI 모듈. 영수증 이미지를 받아 Claude Vision API로 OCR + 카테고리 분류를 수행하고 구조화된 JSON을 반환한다.

## 위치

본 모듈은 `Account` 멀티 모듈 프로젝트의 `account-ai` 모듈로 통합될 예정. 현재는 단독 프로토타입.

```
account/                          # 멀티 모듈 루트
├── account-api      ← REST 엔드포인트, JWT 인증
├── account-core     ← Entity, Repository (MerchantHistoryProvider 구현체)
├── account-ai       ← 본 모듈 (Claude 통합)
└── account-batch    ← 월말 집계, 알림 발송
```

## 모듈 책임

| 책임 | 설명 |
|---|---|
| Claude Vision API 호출 | `ClaudeVisionClient` (RestClient 기반, 가상 스레드 호환) |
| 프롬프트 관리 | `PromptBuilder` + `resources/prompts/receipt-analysis.txt` |
| 가구별 학습 이력 주입 | `MerchantHistoryProvider` 인터페이스 (구현은 account-core) |
| 응답 JSON 추출/파싱 | `ReceiptAnalysisService` (코드 펜스/앞뒤 텍스트 방어) |
| REST 엔드포인트 | `ReceiptController` (POST /api/receipts, multipart) |

## 디렉토리 구조

```
account-ai-prototype/
├── README.md                              ← 본 파일
├── build.gradle.kts                       ← Gradle Kotlin DSL 의존성
└── src/
    ├── main/
    │   ├── java/com/kyuhyeong/account/ai/
    │   │   ├── client/
    │   │   │   └── ClaudeVisionClient.java        ← API 호출 (DTO도 같은 파일)
    │   │   ├── config/
    │   │   │   ├── ClaudeConfig.java              ← RestClient Bean
    │   │   │   └── ClaudeProperties.java          ← @ConfigurationProperties
    │   │   ├── controller/
    │   │   │   └── ReceiptController.java         ← POST /api/receipts
    │   │   ├── model/
    │   │   │   ├── MerchantHistoryContext.java
    │   │   │   └── ReceiptAnalysisResult.java
    │   │   └── service/
    │   │       ├── MerchantHistoryProvider.java   ← 인터페이스 (구현은 core)
    │   │       ├── PromptBuilder.java
    │   │       └── ReceiptAnalysisService.java    ← 진입점
    │   └── resources/
    │       ├── application.yml
    │       └── prompts/
    │           └── receipt-analysis.txt           ← Claude 프롬프트
    └── test/
        └── java/com/kyuhyeong/account/ai/
            └── service/
                └── ReceiptAnalysisServiceTest.java
```

## 설계 결정

### 왜 SDK 대신 RestClient?

Anthropic 공식 Java SDK(`com.anthropic:anthropic-java:2.32.x`)도 있지만 본 프로토타입은 Spring Boot 3.2+의 `RestClient`로 직접 호출한다.

| 항목 | RestClient 직접 호출 | Anthropic SDK |
|---|---|---|
| 의존성 무게 | 매우 가벼움 (Spring 내장) | 추가 ~5MB |
| 가상 스레드 호환 | ✅ 동기 호출이 캐리어 스레드를 점유하지 않음 | △ 내부 OkHttp 풀에 의존 |
| API 변경 대응 | 직접 통제 (헤더, 페이로드) | SDK 업데이트 대기 |
| 디버깅 | 요청/응답 raw로 확인 용이 | 내부 변환 거침 |
| 학습 가치 | 높음 (API 스펙 직접 이해) | 낮음 (블랙박스) |

프로덕션 안정화 후 SDK로 마이그레이션 검토. `build.gradle.kts`에 SDK 의존성 주석으로 표시해둠.

### 왜 multipart 파일 업로드?

영수증은 평균 200KB~1MB JPEG. base64 인코딩하여 JSON 본문에 담는 방식보다 multipart가 ① 대역폭 33% 절감 (base64 오버헤드 회피) ② 클라이언트(Flutter)에서 자연스럽게 처리 가능. 서버에서 base64 인코딩은 Claude API 호출 직전 한 번만 수행.

## 셋업

### 1. Claude API 키 발급

1. [console.anthropic.com](https://console.anthropic.com) 가입
2. **Settings → API Keys → Create Key** 발급 (예: `sk-ant-api03-...`)
3. **Settings → Billing**에서 결제 카드 등록 + **Usage limits** 월 $10 한도 설정 (안전장치)

### 2. 환경변수 설정

```bash
export ACCOUNT_CLAUDE_API_KEY="sk-ant-api03-..."
```

또는 `application-secret.yml` 분리 (KH Shop 패턴):

```yaml
# application-secret.yml (gitignore 대상)
account:
  claude:
    api-key: sk-ant-api03-...
```

`application.yml`에 `spring.profiles.include: secret` 추가하여 자동 로드.

### 3. 빌드 & 실행

```bash
./gradlew :account-ai:bootRun
```

### 4. 호출 테스트

```bash
curl -X POST http://localhost:8080/api/receipts \
  -H "X-Household-Id: 1" \
  -F "image=@/path/to/receipt.jpg"
```

응답 예시:

```json
{
  "result": {
    "date": "2026-05-18",
    "merchant": "스타벅스 강남역사거리점",
    "merchantType": "카페",
    "category": "외식/카페",
    "subcategory": "커피",
    "total": 8500,
    "paymentMethod": "신용카드",
    "items": [
      {"name": "아이스 아메리카노 T", "price": 4500, "quantity": 1},
      {"name": "치즈케이크", "price": 4000, "quantity": 1}
    ],
    "confidence": 0.95
  },
  "autoConfirmable": true,
  "requiresManualClassification": false
}
```

## 신뢰도 기반 처리

| confidence | 처리 |
|---|---|
| ≥ 0.8 | `autoConfirmable: true` → 클라이언트가 즉시 CONFIRMED 거래 생성 |
| 0.5 ~ 0.8 | 사용자가 결과 확인 후 컨펌 (기본값 제시) |
| < 0.5 | `requiresManualClassification: true` → 사용자가 카테고리 직접 선택 |

## 비용 추정

- 영수증 1장당 Claude Sonnet 4.5: 약 ₩20~30
- 부부 2인 월 200건: 약 ₩5,000
- 20명 확장 시 (가구 10개 가정) 월 200건/가구 × 10가구: 약 ₩50,000
- Console에서 월 한도 $10 설정 권장 (폭주 방지)

운영 안정화 후 Haiku 4.5로 다운그레이드 시 4~5배 절감 가능. `application.yml`의 `account.claude.model` 한 줄만 변경.

## 다음 단계 (account-core 통합)

본 프로토타입을 멀티 모듈에 통합할 때 필요한 작업:

1. **`MerchantHistoryProvider` 구현체** in `account-core`:
   ```java
   @Service
   public class JpaMerchantHistoryProvider implements MerchantHistoryProvider {
       private final MerchantHistoryRepository repo;
       
       public MerchantHistoryContext getRecentHistory(Long householdId, int maxEntries) {
           List<MerchantHistory> rows = repo.findTopByHouseholdIdOrderByLastUsedAtDesc(
               householdId, PageRequest.of(0, maxEntries));
           List<MerchantHistoryContext.Entry> entries = rows.stream()
               .map(r -> new MerchantHistoryContext.Entry(
                   r.getMerchantName(),
                   r.getCategory().getName(),
                   r.getCount(),
                   r.getLastUsedAt()))
               .toList();
           return new MerchantHistoryContext(householdId, entries);
       }
   }
   ```

2. **DRAFT 거래 자동 생성** in `account-api`:
   - 분석 결과를 받아 `transactions` 테이블에 DRAFT 상태로 저장
   - `receipts` 테이블에 이미지 경로 + `ocr_raw_json` 저장
   - 응답으로 transaction ID 반환

3. **JWT 통합**:
   - `X-Household-Id` 헤더 대신 JWT 클레임에서 추출
   - `HouseholdContext` (ThreadLocal) 주입
   - Hibernate `@Filter`로 자동 격리

4. **학습 피드백 루프**:
   - 사용자가 카테고리 수정 시 → `merchant_history` UPSERT
   - 점진적으로 가구별 분류 정확도 향상

5. **재시도/회로차단**:
   - Claude API 일시 장애 대비 Resilience4j Retry
   - 5xx 에러는 재시도, 4xx는 즉시 실패

## 테스트

```bash
./gradlew :account-ai:test
```

`ReceiptAnalysisServiceTest`는 Claude API를 모킹하여 다음을 검증:
- 순수 JSON 응답 파싱
- 코드 펜스(```json ... ```) 제거
- 앞뒤 설명 텍스트가 붙은 경우 JSON 추출
- 신뢰도 기반 분기 플래그
- 잘못된 응답에 대한 명확한 예외

실제 Claude API 호출 통합 테스트는 별도 `@IntegrationTest`로 분리 (API 키 + 영수증 샘플 이미지 필요).
