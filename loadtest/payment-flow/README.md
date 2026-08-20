# 결제-예약 확정 부하 검증

## Purpose

`예약 intent 생성 → 결제 승인 요청 → Outbox 워커 → 예약 확정`으로 이어지는 흐름이 동시 부하에서도 **승인된 결제 1건당 예약 정확히 1건**을 만드는지 확인한다.

정확성 실험이며 성능 측정이 아니다. 부하 도구와 앱이 같은 머신에서 도므로 응답시간 절대값은 의미가 없다. 대신 두 가지를 본다.

- **정합성**: 고아 결제, 중복 예약, 미처리 Outbox, 재고 누수 (`verify.sql`)
- **비동기 확정 지연**: 승인 요청부터 `RESERVED` 도달까지 걸린 시간 (`payflow_settle_seconds`)

결제 승인은 API가 Outbox에 적재만 하고 끝나므로, 각 iteration은 fire-and-forget 하지 않고 intent 상태를 폴링해 **종착 상태까지 추적**한다. 반복 수를 고정한 `shared-iterations`를 쓰는 이유도 같다. 기대값이 비율이 아니라 정확한 수가 되어야 `verify.sql`이 판정할 수 있다.

## 사전 조건

Postgres / Redis 기동:

```bash
docker compose -f docker-compose.yml -f docker-compose.rdb.yml up -d postgres redis
```

앱은 **loadtest 결제 게이트웨이로** 기동한다. 기본값은 `toss`이며([TossPaymentsClient](../../apps/stayops-api/src/main/kotlin/com/stayops/payment/infrastructure/external/TossPaymentsClient.kt)가 `matchIfMissing = true`), 그 상태로 돌리면 합성 paymentKey가 실제 Toss API에서 `NOT_FOUND_PAYMENT`로 전부 거절되어 실패 경로만 돌고 외부 API로 대량 트래픽이 나간다.

```bash
STAYOPS_PAYMENT_GATEWAY=loadtest STAYOPS_PAYMENT_LOADTEST_LATENCY_MS=150 \
  ./gradlew :apps:stayops-api:bootRun --args='--spring.profiles.active=local,rdb' \
  > /tmp/stayops-app.log 2>&1
```

`STAYOPS_PAYMENT_LOADTEST_LATENCY_MS`는 PG 왕복 지연을 흉내낸다. 0으로 두면 Outbox 워커가 비현실적으로 빨라져 백로그가 생기지 않는다.

rate limit은 기본값이 비활성이라 별도 조치가 필요 없다. `STAYOPS_RATE_LIMIT_ENABLED=true`로 켜면 `customer-auth-signup`이 IP당 600초에 5회로 제한되어 계정 준비 단계에서 끝난다.

## 실행

```bash
LOG_FILE=/tmp/stayops-app.log ./run.sh 200 20
```

첫 인자는 총 예약 시도 수(기본 200), 둘째는 VU 수(기본 20). `LOG_FILE`을 주면 애플리케이션 로그의 `event=` 집계까지 출력한다.

## 예약 조합 배치

중복 예약 차단은 `memberId + roomTypeId + checkIn + checkOut`으로 판정한다. 따라서 매 iteration은 서로 다른 조합을 써야 하고, `ITERATIONS <= CUSTOMERS * STAY_DAYS`가 아니면 setup에서 실패한다.

**날짜가 가장 빠르게 변한다.** 동시에 도는 iteration이 서로 다른 재고 행을 만지게 하려는 의도다. 고객은 날짜를 한 바퀴 돌고 나서야 바뀐다.

`HOT_DATE_ONLY=true`를 주면 모든 예약이 한 날짜로 몰린다. 이때 측정 대상은 결제 경로가 아니라 **그 재고 행의 경합**이 되고, 대부분의 요청이 낙관적 락 CAS에서 탈락해 결제에 닿지 못한다(20 VU에서 200건 중 195건이 `409 INVENTORY_CONFLICT`). 재고 경합 자체의 검증은 [optimistic-lock](../optimistic-lock/README.md) 실험이 담당한다.

## 판정 기준

`verify.sql`의 10개 섹션 중 1번만 참고용이고, 나머지는 모두 `OK`여야 한다.

| 확인 항목 | 합격 조건 |
|---|---|
| 승인 결제 수 == 예약 수 | 일치 |
| 예약 없이 승인된 결제 | 0건 |
| RESERVED intent의 결제/예약 연결 | 누락 0건 |
| 동일 조건 중복 예약 | 0건 (중복 승인 요청이 예약을 2건 만들지 않는다) |
| 미처리 / 실패 Outbox | 0건 |
| 재고 `held_count` 잔여 | 0 |
| 재고 `reserved_count` == 확정 예약 숙박일 수 | 일치 |
| 종료되지 않은 hold | 0건 |
| 정리되지 않은 활성 예약 시도 | 0건 |

**`held_count` 잔여와 미처리 Outbox는 같은 결함을 두 각도에서 본 것**일 수 있다. Outbox가 `IN_PROGRESS`로 멈추면 hold도 해제되지 않는다.

## 로그로 흐름 추적하기

모든 결제 로그는 `[한글 라벨] event=snake_case key=value` 형식이고, `reservationIntentId`가 동기 요청과 비동기 워커를 잇는 상관 키다. 예약 1건을 끝까지 따라가려면:

```bash
grep "reservationIntentId=<id>" /tmp/stayops-app.log
```

정상 경로는 다음 순서로 나타난다.

```
reservation_intent_created      # intent + 결제 + hold 생성
payment_confirm_requested       # Outbox 적재
payment_confirm_accepted        # API 202 응답
payment_outbox_claimed          # 워커 선점
payment_pg_confirm_started
payment_pg_confirm_succeeded
payment_reservation_finalized   # 예약 생성 + hold 소비
payment_outbox_batch_completed  # 배치 요약
```

집계로 이상을 먼저 찾는다.

```bash
grep -o 'event=[a-z_]*' /tmp/stayops-app.log | sort | uniq -c | sort -rn
```

읽는 요령:

- `payment_pg_confirm_succeeded` > `payment_reservation_finalized` → **PG 승인 후 내부 확정 실패**. 가장 나쁜 부류다. `payment_reservation_finalization_failed`의 `errorType`을 본다.
- `payment_confirm_accepted` > `payment_confirm_requested` 아님 → 정상. 둘은 같이 움직인다.
- `payment_confirm_ignored` 다수 → 재요청이 이미 종료된 결제에 도달. 202 응답만으로는 구분되지 않으므로 이 로그가 유일한 단서다.
- `payment_outbox_batch_completed`의 `claimed == batchCapacity` → 배치가 상한을 채웠다는 뜻이고, 워커가 유입 속도를 못 따라가고 있을 가능성.
- `PaymentOutbox scheduler 처리 실패` → 보상 처리 자체가 예외를 던져 메시지가 `IN_PROGRESS`로 남았다. Outbox 잔여의 직접 원인이다.

## 파일

| 파일 | 역할 |
|---|---|
| `seed.sql` | payflow 소유 데이터만 정리 후 재고 생성. run.sh가 `:days` / `:total` / `:start_offset`을 주입 |
| `payment-flow.js` | k6 시나리오. intent 생성 → 결제 승인 → 종착 상태 폴링 |
| `verify.sql` | 실행 후 정합성 10개 검증 |
| `run.sh` | 시드 → k6 → Outbox 소진 대기 → 검증 → 로그 집계 |
