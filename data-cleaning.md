# 운영 DB 데이터 클리닝 (dev 시드 전체 제거)

> ⚠ **운영(production) 데이터 삭제 절차 — 비가역.** 반드시 §0 백업 후 진행.
> 대상: VPS 의 `account-app-mariadb-prod` 컨테이너 `account` 스키마.
> **상태: 아직 미적용 (2026-06-03 기준).**

## 배경

Flyway 시드(`V2__seed_dev` + `V3__seed_dev_bcrypt_passwords`)가 운영 DB 에도 그대로 적용되어
**테스트 가구 2개(우리집 id 1, 테스트가구 id 2) + 유저 4명(id 1~4)** 이 들어있다.

2026-06-02 카카오 OAuth2 전환 이후 실제 로그인은 본인 카카오 계정으로 한다. 운영에서
`account.dev.kakao-links` 를 비워두면(기본) 첫 로그인 시 시드와 **무관한 신규 유저 + 신규 가구**가
온보딩으로 생성된다. 즉 시드 데이터는 더 이상 쓰이지 않는 잔재 → **전부 삭제**하고 본인 카카오로 만든
실 가구만 남긴다.

| 대상 | 처리 |
|---|---|
| 시드 household 1(우리집), 2(테스트가구) + 자식 데이터 전부 | **삭제** |
| 시드 users 1~4 (owner1/member1/owner2/member2) | **삭제** |
| 본인 카카오로 생성된 신규 유저 + 가구 (id ≥ 5 / 가구 id ≥ 3) | **보존** |

> 시드 household 1 의 기존 거래/영수증을 이어받고 싶다면 삭제하지 말고 맨 아래 [대안](#대안-시드-데이터를-이어받고-싶다면) 참조.
> (B 결정: 카카오로 새로 시작 → 시드는 버림.)

## FK 정책 (중요)

모든 도메인 테이블 FK 는 `ON DELETE RESTRICT` (cascade 없음). 따라서 **자식 테이블부터** 순서대로
삭제하고, 전체를 트랜잭션으로 묶어 검증 후 `COMMIT`/`ROLLBACK` 한다.

## 0. 백업 (필수)

```bash
docker exec account-app-mariadb-prod sh -c 'exec mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" account' \
  > /root/account-backup-$(date +%F).sql
ls -lh /root/account-backup-*.sql      # 파일 생성 확인
```

## 1. 삭제 전 — 보존 대상 확인

삭제 대상이 가구 1·2 + 유저 1~4 뿐이고, 본인 실 가구/유저는 그 밖(id ≥ 3 / ≥ 5)인지 **눈으로 검증**한다.

```bash
docker exec -it account-app-mariadb-prod mariadb -u root -p account
```
```sql
SELECT id, name, owner_user_id FROM households ORDER BY id;
-- 1=우리집(시드), 2=테스트가구(시드), 그 외 id=본인 카카오 실 가구(보존)
SELECT id, provider, provider_user_id, name FROM users ORDER BY id;
-- id 1~4 = 시드(provider_user_id 보통 NULL), id ≥ 5 = 본인 카카오(provider_user_id 채워짐, 보존)
```

## 2. 시드 가구·유저 전체 삭제

```sql
START TRANSACTION;

-- 자식 → 부모 순서 (RESTRICT). 시드 두 가구(1,2) 모두 대상.
DELETE FROM transaction_history    WHERE household_id IN (1, 2);
DELETE FROM recurring_transactions WHERE household_id IN (1, 2);
DELETE FROM transactions           WHERE household_id IN (1, 2);
DELETE FROM merchant_history       WHERE household_id IN (1, 2);
DELETE FROM receipts               WHERE household_id IN (1, 2);
DELETE FROM assets                 WHERE household_id IN (1, 2);
DELETE FROM liabilities            WHERE household_id IN (1, 2);
DELETE FROM categories             WHERE household_id IN (1, 2);
DELETE FROM invite_codes           WHERE household_id IN (1, 2);
DELETE FROM household_members      WHERE household_id IN (1, 2);
DELETE FROM households             WHERE id IN (1, 2);
DELETE FROM users                  WHERE id IN (1, 2, 3, 4);

-- 검증: 시드가 0, 총계는 본인 실 데이터만 남았는지
SELECT (SELECT COUNT(*) FROM households WHERE id IN (1,2)) AS seed_households_left,  -- 0
       (SELECT COUNT(*) FROM users      WHERE id IN (1,2,3,4)) AS seed_users_left,   -- 0
       (SELECT COUNT(*) FROM households) AS households_total,   -- 본인 실 가구 수
       (SELECT COUNT(*) FROM users)      AS users_total;        -- 본인 실 유저 수

-- 정상이면 COMMIT;  이상하면 ROLLBACK;
```

> ⚠ `users` 삭제가 FK 로 막히면(`Cannot delete or update a parent row`), 그 유저가 만든 거래/영수증/
> 이력/초대코드가 **보존 대상 가구에 남아 있다**는 뜻이다 — 위 자식 삭제는 시드 가구(1,2) 한정이므로,
> 막혔다면 해당 유저는 시드가 아니라 실사용 유저일 수 있다. **즉시 `ROLLBACK` 후 §1 로 재확인.**

## 복구 (문제 시)

```bash
docker exec -i account-app-mariadb-prod sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" account' \
  < /root/account-backup-YYYY-MM-DD.sql
```

## 참고

- 카카오 OAuth2 단독 인증(2026-06-02) 이후 비번 로그인은 폐지 — 시드 유저의 `password_hash` 는 무의미.
- 가구 멤버/초대코드 관리는 `/web/admin`(가구 설정, OWNER 전용)에서 — 초대코드 발급으로 배우자 합류 처리.

## 대안: 시드 데이터를 이어받고 싶다면

시드 우리집(household 1)의 거래/영수증을 본인 카카오 계정으로 보존하려면 §2 대신:

1. 운영 `application-secret.yml`(또는 env)에 일시적으로
   `account.dev.kakao-links: { "<내 providerUserId>": "owner1@example.com" }` 설정 후 재기동
   (`providerUserId` 는 카카오 로그인 1회 시도 시 서버 로그 `Kakao login: providerUserId=...` 에서 확인)
2. 본인 카카오로 로그인 → owner1(id 1)에 연결되어 household 1 데이터로 진입
3. 매핑을 다시 비우고 재기동 (운영은 비워두는 게 기본)
4. 그 뒤 §2 에서 범위를 테스트가구만으로 좁혀 삭제 — `IN (1, 2)` → `= 2`, `users ... IN (1,2,3,4)` → `IN (3, 4)`
   (member1[id 2]은 배우자가 카카오로 재합류하면 되므로 함께 삭제)
