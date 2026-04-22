# StayOps k6 Load Test

## Purpose

이번 k6 스크립트는 **standalone MongoDB 기준의 read-heavy 숙소 조회 부하**를 검증하기 위한 것이다.

현재 단계는 아래를 하지 않는다.

- failover / recovery
- authenticated write flow
- reservation create / payment confirm
- stress / spike

이 제한은 구현 전제와 맞춘 것이다. 현재 예약 write path는 Mongo transaction을 사용하므로, true standalone MongoDB에서 write 부하를 섞으면 결과 해석이 왜곡된다.

## Scenarios

### `stayops-app-load.js`

가벼운 앱 baseline 용도다.

- `GET /actuator/health`
- `GET /actuator/info`

DB 부하가 거의 없는 상태에서 앱과 네트워크의 기본 레이턴시를 본다.

### `stayops-db-load.js`

production-like read mix 용도다.

- `50%` property list
  - `GET /api/v1/customer/properties`
- `20%` property detail exploration
  - `GET /api/v1/customer/properties/{propertyId}`
  - `GET /api/v1/customer/properties/{propertyId}/room-types`
- `30%` property offers
  - `GET /api/v1/customer/properties/{propertyId}/offers?checkIn=&checkOut=&guests=`

핫한 숙소 몇 개에 상세 / offers 트래픽이 몰리는 상황을 기본값으로 둔다.

## Files

- `common.js`
- `stayops-app-load.js`
- `stayops-db-load.js`
- `package.json`

`package.json`은 `node --check` 검증 시 ESM 문법을 허용하기 위한 최소 설정이다.

## Modes

### App script

- `MODE=smoke`
- `MODE=app-baseline`

### DB script

- `MODE=smoke`
- `MODE=db-baseline`
- `MODE=db-ramp`

## Environment variables

### Common

- `BASE_URL`
  - default: `http://localhost:8080`

### App script

- `APP_RATE`
  - default: `5`
- `APP_PRE_ALLOCATED_VUS`
  - default: `10`
- `APP_MAX_VUS`
  - default: `50`
- `APP_THINK_TIME`
  - default: `0.2`

### DB script

- `DB_RATE`
  - default: `12`
- `DB_PRE_ALLOCATED_VUS`
  - default: `20`
- `DB_MAX_VUS`
  - default: `100`
- `DB_THINK_TIME`
  - default: `0.5`
- `HOT_PROPERTY_COUNT`
  - default: `5`
- `HOT_PROPERTY_IDS`
  - optional csv list
- `SEARCH_RATE`
  - default: `0.5`
- `DETAIL_RATE`
  - default: `0.2`
- `OFFERS_RATE`
  - default: `0.3`
- `CHECK_IN_OFFSETS`
  - default: `3,5,7,14,21,30`
- `NIGHTS_POOL`
  - default: `1,2,3`
- `GUESTS_POOL`
  - default: `1,2,3,4`
- `DB_RAMP_START`
  - default: `8`
- `DB_RAMP_STEP_1`
  - default: `16`
- `DB_RAMP_STEP_2`
  - default: `24`
- `DB_RAMP_STEP_3`
  - default: `32`

## Run examples

```bash
cd loadtest/k6
```

### App smoke

```bash
BASE_URL=http://localhost:8080 MODE=smoke k6 run stayops-app-load.js
```

### App baseline

```bash
BASE_URL=http://localhost:8080 MODE=app-baseline APP_RATE=5 k6 run stayops-app-load.js
```

### DB smoke

```bash
BASE_URL=http://localhost:8080 MODE=smoke k6 run stayops-db-load.js
```

### DB baseline

```bash
BASE_URL=http://localhost:8080 \
MODE=db-baseline \
SEARCH_RATE=0.50 \
DETAIL_RATE=0.20 \
OFFERS_RATE=0.30 \
HOT_PROPERTY_COUNT=5 \
DB_RATE=12 \
k6 run stayops-db-load.js
```

### DB ramp

```bash
BASE_URL=http://localhost:8080 \
MODE=db-ramp \
HOT_PROPERTY_IDS=property-1,property-7,property-9 \
DB_RAMP_START=8 \
DB_RAMP_STEP_1=16 \
DB_RAMP_STEP_2=24 \
DB_RAMP_STEP_3=32 \
k6 run stayops-db-load.js
```

## Verification

```bash
node --check common.js
node --check stayops-app-load.js
node --check stayops-db-load.js
```

## Notes

- `availability` / `rates`는 이번 단계의 대표 시나리오가 아니다.
- 필요하면 2차 진단용으로 별도 스크립트에 분리한다.
- property list가 비어 있으면 setup 단계에서 즉시 실패한다.
