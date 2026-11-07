# 낙관적 락 동시성 검증

## Purpose

재고 1개를 두고 동시에 들어온 예약 요청 중 **정확히 1건만 성공**하고, 나머지는 낙관적 락이 막아내는지를 실제 HTTP 요청으로 확인한다.

단위 테스트([RdbRoomInventoryRepositoryTest](../../apps/stayops-api/src/test/kotlin/com/stayops/inventory/infrastructure/persistence/rdb/RdbRoomInventoryRepositoryTest.kt))가 저장소 계층을 검증한다면, 이 스크립트는 인증·검증·재고 점유를 모두 통과하는 실제 요청 경로를 검증한다.

이 검증은 **정확성**을 보는 실험이며 성능 측정이 아니다. 부하 도구와 앱이 같은 머신에서 도므로 응답시간 수치는 의미가 없다.

## 사전 조건

Postgres / Redis 기동:

```bash
docker compose -f docker-compose.yml -f docker-compose.rdb.yml up -d postgres redis
```

앱 기동:

```bash
./gradlew :apps:stayops-api:bootRun --args='--spring.profiles.active=local,rdb'
```

rate limit은 기본값이 비활성이라 별도 조치가 필요 없다. `STAYOPS_RATE_LIMIT_ENABLED=true`로 켜면 `customer-auth-signup`이 IP당 600초에 5회로 제한되어 계정 준비 단계가 재고에 닿기도 전에 429로 끝나므로, 이 검증을 돌릴 때는 켜지 않는다.

## 실행

```bash
LOG_FILE=/path/to/app.log ./run.sh 30
```

첫 인자는 동시 요청 수(기본 30). `LOG_FILE`을 주면 애플리케이션 로그 집계까지 출력한다.

## 기대 결과

```
===== 요청 결과 =====
  20 400 ROOM_NOT_AVAILABLE
   9 409 INVENTORY_CONFLICT
   1 201 NO_CODE

===== 재고 최종 상태 =====
 total_count | reserved_count | held_count | blocked_count | version | available
           1 |              0 |          1 |             0 |       1 |         0

===== 애플리케이션 로그 (이번 실행분) =====
[재고 저장] inventoryId=oplock-inv-1, date=2026-09-01, version=0->1, held=1, reserved=0, available=0
   9 [재고 낙관적 락 충돌] inventoryId=oplock-inv-1, ..., attemptedVersion=0
-- 충돌을 기록한 스레드 수: 9
```

### 판정 기준

| 확인 항목 | 합격 조건 |
|---|---|
| 성공 건수 | 정확히 1건 (`201`) |
| 재고 상태 | `held_count = 1`, `version = 1`, `available = 0` |
| 충돌 로그 | `attemptedVersion`이 모두 동일 (전부 같은 스냅샷을 읽음) |
| 스레드 수 | 충돌 건수와 같음 (병렬 처리 증거) |

`version`이 2 이상이면 CAS를 뚫고 두 번 갱신된 것이므로 즉시 실패다.

### 두 갈래 실패의 의미

패자는 두 지점 중 하나에서 걸린다.

- **`409 INVENTORY_CONFLICT`** — 승자와 같은 스냅샷을 읽고 저장까지 갔다가 version 불일치로 탈락. 낙관적 락이 직접 막은 요청이다.
- **`400 ROOM_NOT_AVAILABLE`** — 승자가 이미 저장을 마친 뒤 도착해, 재고 점유 전 사전 가용성 검사에서 걸러진 요청.

둘의 비율은 타이밍에 따라 매 실행 달라진다. 판정 기준은 비율이 아니라 **성공 1건과 재고 정합성**이다.

### 조심할 것

`DUPLICATE_RESERVATION`이 결과에 섞이면 **실험이 무효다.** 중복 예약 차단은 `memberId + roomTypeId + checkIn + checkOut`으로 판정하므로, 한 계정으로 반복 발사하면 재고에 닿기 전에 막힌다. 이때도 겉보기 결과는 "성공 1, 실패 29"로 기대값과 같아 보이지만 낙관적 락은 한 번도 동작하지 않은 상태다. `run.sh`가 매 실행 계정 30개를 준비하고 이전 실행의 intent를 지우는 이유가 이것이다.

## 파일

| 파일 | 역할 |
|---|---|
| `seed.sql` | 이전 실행 데이터 정리 후 `total_count = 1` 재고 생성 |
| `run.sh` | 계정 준비 → 워밍업 → 동시 발사 → 결과 집계 |
| `fire.sh` | 요청 1건 발사 (xargs 병렬 실행 단위) |
