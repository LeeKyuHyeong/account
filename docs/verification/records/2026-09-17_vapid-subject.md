# VAPID subject 를 비개인 값으로 (운영 전달 경로 추가)
- 일자: 2026-09-17
- 유형: 수정
- 우선순위: P2 (설정)
- 판정: 조건부 (배포 후 운영 값·테스트 알림은 🙋)

## 1. 요청과 목적
- 사용자가 원한 것: Web Push VAPID subject 에 개인 이메일·자리표시자가 쓰이지 않게 하고, 운영 `.env.prod` 값이 컨테이너에 전달되게 한다
- 배경: 2026-09-16 히스토리 재작성으로 `application.yml` 기본값이 `mailto:<OWNER_EMAIL>` 자리표시자가 됐고, `docker-compose.prod.yml` 은 `ACCOUNT_PUSH_VAPID_SUBJECT` 를 넘기지 않았다. 운영 실측(09-17): `docker exec account-api printenv ACCOUNT_PUSH_VAPID_SUBJECT` → 빈 값 = yml 기본값 사용 중
- 결정: 기본값 `https://account.kyuhyeong.com`(메일함 불필요, 개인정보 없음). 운영 실제 값은 사용자가 서버 `.env.prod` 에 입력(09-17)

## 2. Acceptance Criteria
| # | 구분 | 조건 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | 정상 | 운영 컨테이너에 `.env.prod` 의 `ACCOUNT_PUSH_VAPID_SUBJECT` 가 전달된다 | 🙋 | 배포 후 `printenv` |
| 2 | 예외 | `.env.prod` 에 값이 없어도 개인정보 없는 기본값으로 동작한다 | ✅ | compose `:-https://…` + yml 기본값 (빈 문자열이 기본값을 덮지 않도록 compose 쪽에도 기본값) |
| 3 | 노출 | 저장소 어디에도 개인 이메일·자리표시자 subject 가 없다 | ✅ | `grep -rn "OWNER_EMAIL" --include=*.yml` → 0 |
| 4 | 연쇄 | 푸시 발송(테스트 알림)이 계속 동작한다 | 🙋 | 알림 설정 화면 테스트 알림 |

## 3. 변경 사항
- `account-api/src/main/resources/application.yml` — subject 기본값 `mailto:<OWNER_EMAIL>` → `https://account.kyuhyeong.com`
- `docker-compose.prod.yml` — `ACCOUNT_PUSH_VAPID_SUBJECT: ${ACCOUNT_PUSH_VAPID_SUBJECT:-https://account.kyuhyeong.com}` 추가
- `.env.prod.example` — VAPID 키 2개 + subject 항목 추가(값은 자리표시자/비개인 값)
- 코드 변경 없음 (`PushSendService` 는 `account.push.vapid.subject` 를 그대로 읽음)

## 4. 영향 범위 분석
- subject 사용처: `PushSendService.java:47-49` 1곳(VAPID JWT 의 `sub`). 키 쌍은 그대로라 기존 구독에 영향 없음
- 배포: `ci.yml` 이 `--env-file .env.prod` 로 `up -d account-api` — 서버 `.env.prod` 값이 이번 배포부터 전달됨

## 5. 실행한 검증
| 계층 | 명령/방법 | 결과 | 상태 |
|---|---|---|---|
| 정적 | `git diff --check`, placeholder grep | 이상 없음 / 0건 | ✅ |
| 빌드·테스트 | `./gradlew build` | 로컬에 JDK 21 없음 → CI(`Backend (Gradle build + test)`)에서 실행 | 🙋 (Actions) |
| 운영 | `docker exec account-api printenv ACCOUNT_PUSH_VAPID_SUBJECT` | 배포 후 확인 | 🙋 |

## 6. 수동 확인 시나리오
1. [전제] push 후 Actions 성공
2. [행동] 서버에서 `docker exec account-api printenv ACCOUNT_PUSH_VAPID_SUBJECT`
3. [기대] `.env.prod` 에 넣은 값이 출력됨
4. [행동] 앱 알림 설정 화면에서 테스트 알림 → [기대] 수신
- 결과: ☐ 통과 ☐ 실패

## 7. checklist 점검
- 점검함: 비밀값·개인정보 노출(없음), 설정 기본값 폴백(빈 문자열 대응)
- 해당 없음: DB·트랜잭션·권한

## 8. 발견된 문제와 조치
- compose 가 subject 를 넘기지 않아 운영 `.env.prod` 값이 무시되던 것 → 전달 추가

## 9. 미검증 영역과 남은 위험
- 푸시 서비스(FCM·Apple 등)가 https subject 를 거부하는지는 테스트 알림으로만 확인 가능

## 10. Regression 등록
- 없음
