# AWS MongoDB Standalone Load Test Plan

## Status

- Owner: backend
- Status: active
- Topology: `local k6 -> app EC2 -> standalone MongoDB EC2`
- Current phase excludes failover, recovery, stress, and write-path validation

## Goal

이번 단계의 목적은 저비용 토폴로지에서 **숙소 조회 트래픽이 App 서버와 standalone MongoDB에 어떤 부하를 주는지** 먼저 확인하는 것이다.

Replica set 기반 failover 실험은 뒤로 미룬다. 현재 애플리케이션의 예약 생성 경로는 `MongoTransactionManager`와 `@Transactional`을 전제로 작성되어 있으므로, true standalone MongoDB에서 write-path 결과를 해석하면 부하 한계가 아니라 **배포 토폴로지 불일치**가 섞인다.

## Why read-heavy first

숙소 시스템에서 가장 빈도가 높은 요청은 조회다. 다만 조회도 무게가 다르다.

- `GET /api/v1/customer/properties`
  - 현재 구현은 `propertyRepository.findAll()` 이후 애플리케이션 레벨에서 `isBookable()` 필터를 적용한다.
- `GET /api/v1/customer/properties/{propertyId}/offers`
  - 숙소를 하나 고른 뒤 객실 타입별로 재고와 요금제를 다시 읽는다.
  - 실제 예약 직전 사용자가 여러 번 반복할 가능성이 큰 조회다.

현재 `main` 기준 공개 검색 API는 날짜/인원 파라미터를 받지 않는다. 그래서 이번 계획의 현실적인 read 시나리오는 아래 흐름으로 정의한다.

1. 목록 조회
2. 인기 숙소 상세 조회
3. 인기 숙소 offers 조회

날짜와 인원으로 인한 무거운 읽기는 `offers`에 집중된다.

## Scope

### In

- standalone MongoDB 기준 read-heavy 시나리오
- production-like access pattern
- hot-property concentration
- app baseline 측정
- db read saturation 측정

### Out

- MongoDB failover / recovery / election / oplog catch-up
- reservation create / payment confirm / cancel
- authenticated write scenario
- spike / stress test

## Evidence from code

- `src/main/kotlin/com/stayops/reservation/application/service/ReservationSearchApplication.kt`
  - `searchProperties()` -> `propertyRepository.findAll()`
  - `getReservationOffers()` -> room type별 inventory / ratePlan fan-out
- `src/main/kotlin/com/stayops/shared/config/MongoConfig.kt`
  - `MongoTransactionManager` 등록
- `src/main/kotlin/com/stayops/reservation/application/service/CustomerReservationApplication.kt`
  - `createReservation()` / `confirmPayment()` / `cancelReservation()`에 `@Transactional`

## Traffic model

### Primary mix

- `50%` property list
  - endpoint: `GET /api/v1/customer/properties`
  - role: 검색 진입, 전체 목록 응답 비용 측정
- `20%` property detail exploration
  - endpoints:
    - `GET /api/v1/customer/properties/{propertyId}`
    - `GET /api/v1/customer/properties/{propertyId}/room-types`
  - role: 상세 진입 이후 숙소/객실 기본 정보 확인
- `30%` offers comparison
  - endpoint: `GET /api/v1/customer/properties/{propertyId}/offers?checkIn=&checkOut=&guests=`
  - role: 예약 직전 비교, 날짜 기반 읽기 부하 측정

### Hot-property concentration

- 인기 숙소는 `HOT_PROPERTY_IDS` 환경변수로 직접 주입하거나
- 초기 property list 응답의 앞쪽 `N`개를 hot set으로 사용한다.
- 기본 hot set 크기: `5`

### Date and guest distribution

- check-in offset pool: `3,5,7,14,21,30`
- nights pool: `1,2,3`
- guests pool: `1,2,3,4`

가까운 미래 날짜와 소수 인기 숙소에 조회가 몰리는 패턴을 기본 가정으로 둔다.

## Execution modes

### `smoke`

- 목적: 경로와 응답 형식 확인
- duration: `30s`
- low VU / low rate

### `app-baseline`

- 대상: `/actuator/health`, `/actuator/info`
- 목적: DB 부담이 거의 없는 상태에서 앱/네트워크 기본 레이턴시 확인
- 추천 구성:
  - warm-up `2m`
  - steady `10m`
  - cooldown `1m`

### `db-baseline`

- 대상: read mix
- 목적: 현재 스펙에서 안정적으로 유지 가능한 조회 처리량 확인
- 추천 구성:
  - warm-up `2m`
  - steady `10m`
  - cooldown `1m`

### `db-ramp`

- 대상: read mix
- 목적: 레이턴시 급등, dropped iterations, App/Mongo saturation이 시작되는 구간 확인
- 권장 방식:
  - 일정 구간별 rate step-up
  - 마지막에 cooldown

## Success criteria

### Must measure

- latency: p50 / p90 / p95 / max
- error rate
- throughput
- dropped iterations
- app CPU / memory
- MongoDB CPU / memory / connection / operation trend

### Decision points

- `db-baseline`에서 p95가 안정적으로 유지되는가
- `db-ramp`에서 어느 구간부터 p95가 급격히 튀는가
- 먼저 포화되는 쪽이 App인지 MongoDB인지 구분되는가
- hot-property 집중 시 `offers`가 전체 mix의 주 병목인지 확인되는가

## Deliverables

- plan 문서
- runbook
- standalone override compose
- k6 script
- 블로그 초안

## Next phase

다음 단계로 넘어가려면 아래 조건 중 하나가 필요하다.

1. MongoDB를 single-node replica set으로 전환해서 현재 write-path를 보존한다.
2. MongoDB를 multi-node replica set으로 전환해 failover / recovery와 write-path를 함께 검증한다.

이 조건이 충족되기 전까지는 standalone 실험 결과를 **read-heavy 용량 추정**으로만 사용한다.
