# Phase 11: Customer Booking (Finestay) — Context

## 진행 상황

| Sub-step | 내용 | 상태 | 커밋 |
|----------|------|------|------|
| 11-1 | Payment 도메인 모델 + 단위 테스트 | 완료 | `d4a745f` |
| 11-2 | MemberRole.CUSTOMER + 고객 인증 서비스 | 완료 | `8bf237e` |
| 11-3 | Security Config + 고객 인증 API | 완료 | `342d12c` |
| 11-4 | 숙소 검색 API (Public) | 완료 | `28ea3d8` |
| 11-5 | Reservation 도메인 변경 (memberId, expiresAt) | 완료 | `f190bb0` |
| 11-6 | Payment 인프라 (MongoDB + Toss Client) | 완료 | `cb4b5da` |
| 11-7 | BookingApplication (핵심 오케스트레이션) | 완료 | `ce2610c` |
| 11-8 | Booking API + MyPage API | 완료 | `8e5b74c` |
| 11-9 | PENDING TTL 스케줄러 | 완료 | `11b8382` |
| 11-10 | E2E 통합 테스트 | 완료 | 미커밋 |

## 설계 결정

### D1: MongoDB 유지 (RDB 도입 안 함)
- Payment 정합성을 위해 RDB 도입 검토했으나, Reservation이 MongoDB에 있어 cross-DB 트랜잭션 문제 발생
- MongoDB Replica Set 환경이므로 멀티 도큐먼트 트랜잭션 사용 가능
- `MongoTransactionManager` Bean은 이미 등록되어 있으나 `@Transactional`을 아직 어디에서도 사용하지 않음 → Phase 11-7에서 적용 예정

### D2: Toss 결제 — 직접 호출 + 보상 트랜잭션
- Outbox 패턴 검토했으나, 결제 confirm은 프론트엔드가 즉시 결과를 기다리는 동기 플로우
- Outbox로 비동기 처리 시 polling/SSE 필요 → 현재 규모에서 과한 설계
- Toss 조회 API로 모호한 상태 복구하는 fallback 방식 채택

### D3: PaymentGateway 인터페이스 도입 + 다중 PG 확장 고려
- `PaymentGateway` 인터페이스(domain 레이어)를 두고 `TossPaymentsClient`(infrastructure)가 구현하는 구조 적용
- 현재는 Toss 단일 PG이므로 단일 인터페이스 주입으로 충분
- **향후 다중 PG 지원 시**: f-lab-edu/payment-system 프로젝트의 Strategy Map 패턴 참고
  - `Map<PGCompany, PaymentGateway>` 로 런타임 PG 선택
  - PG별 엔티티 분리 (`KakaoPayment`, `TossPayment` 등)
  - PG별 RequestBodyFactory로 요청 포맷 차이 흡수
  - URL 경로에 PG사 지정 (`/api/v1/payment/{pgCompany}/...`)
- **보상 트랜잭션**: payment-system은 Spring Batch로 PG 정산 데이터와 DB를 주기적 비교하여 불일치 해소 — 프로덕션에서는 필수이나 현재 Phase 11 범위에서는 미적용
- 실제 프로덕션에서는 수수료 협상, 장애 대비(fallback), 결제 수단 커버리지 등의 이유로 다중 PG 사용이 일반적

### D4: SecurityConfig 구조 유지
- 엔드포인트 추가 시 체이닝이 늘어나는 문제 인식
- 현재 SecurityConfig은 인증 여부(permitAll vs authenticated)만 판단
- 세부 인가는 PropertyAccessChecker / CustomerAuthChecker에서 처리
- role 기반 분기가 복잡해지면 @PreAuthorize 도입 재검토

## 미해결 이슈

### C1 (CRITICAL): @Transactional 누락
- 기존 `ReservationApplication`의 `createReservation()`, `cancelReservation()`, `checkInReservation()`, `checkOutReservation()` 모두 다중 도큐먼트 write에 트랜잭션 없음
- Phase 11-7에서 BookingApplication에 적용할 때 기존 코드도 함께 수정할지, 별도 작업으로 분리할지 결정 필요

### C2: PENDING TTL 스케줄러 실시간성 개선
- 현재 `@Scheduled(fixedRate = 60_000)`로 1분마다 만료 예약 처리 → 최대 1분 지연
- **개선 방안**: Redis Keyspace Notification 조합
  - 예약 생성 시 Redis에 `SET pending:{reservationId} EX 900` (15분 TTL) 저장
  - Redis 키 만료 이벤트 수신 → 즉시 취소 + 재고 복원 (실시간)
  - `@Scheduled`는 이벤트 유실 대비 안전망으로 유지
  - Redis는 이미 프로젝트에 포함되어 있으므로 인프라 추가 불필요
- Redis Keyspace Notification은 전달 보장 없음(서버 다운 시 유실) → 반드시 @Scheduled와 병행

### C3: MongoDB 통합 테스트 주의사항
Phase 11-10 E2E 테스트 작성 중 발견된 사항:

**Replica Set 연결 필수**
- `@Transactional`은 MongoDB Replica Set에서만 동작
- Testcontainers `@ServiceConnection`은 Standalone URL을 제공하여 트랜잭션 실패
- `mongo.replicaSetUrl`을 `DynamicPropertyRegistrar`로 직접 등록해야 함

**데이터 정리: deleteMany vs dropCollection**
- `dropCollection`은 컬렉션과 함께 유니크 인덱스도 삭제 → `@PostConstruct`로 생성한 인덱스가 복구 안 됨 → 다른 테스트의 DuplicateKeyException 검증 실패
- `deleteMany(Document())`로 도큐먼트만 삭제하고 인덱스를 보존해야 함

**@Version과 save() 동작**
- version 0인 객체를 `save()` → insert 수행
- save()가 반환한 객체(version 1+)로 다시 save() → update 수행
- version 0인 원본 객체로 다시 save() → insert 시도 → DuplicateKeyException
- 상태 변경 후 저장 시 반드시 save() 반환값을 사용할 것

**@MockkBean 초기화**
- `lateinit var`로 선언 시 생성자 주입 시점에 초기화되지 않을 수 있음
- 생성자 파라미터에 `@MockkBean`을 직접 선언하는 것이 안전

### C4 (해결됨): cancelBooking() PENDING 취소 시 NPE
- **문제**: `cancelBooking()`이 CONFIRMED 취소만 고려하여 PENDING 취소 시 3중 에러 발생
  - `payment.paymentKey!!` → NPE (Toss 승인 전이므로 null)
  - `payment.cancel()` → IllegalStateException (APPROVED에서만 가능)
  - `reservation.cancel()` → IllegalStateException (CONFIRMED에서만 가능)
- **해결**: `reservation.status`에 따라 분기
  - PENDING: `cancelPending()` + `payment.fail()` — Toss 환불 호출 없음
  - CONFIRMED: 기존 로직 유지 — Toss 환불 + `payment.cancel()` + `reservation.cancel()`
- **커밋**: 코드 리뷰 C1 수정

### C5 (이슈 없음): inventoryApplication.reserve() 트랜잭션 참여 여부
- **우려**: `createBooking()`의 `@Transactional` 안에서 호출되는 `inventoryApplication.reserve()`가 별도 트랜잭션을 시작하면, rollback 시 재고만 차감된 채 남을 수 있음
- **확인 결과**: `reserve()`에 `@Transactional`이 없으므로 부모 트랜잭션에 자연스럽게 참여 → 문제 없음
- **참고**: `reserve()` 내부의 Redis 캐시 evict는 MongoDB 트랜잭션에 포함되지 않지만, 캐시 evict는 "다음 조회 시 DB에서 다시 읽기"이므로 정합성 영향 없음

### C6: OWNER / MANAGER role 구분 없음
- `MemberRole.OWNER`와 `MemberRole.MANAGER`가 코드상 동일하게 동작
- `PropertyRole.OWNER`와 `PropertyRole.MANAGER`를 분기하는 로직 없음
- YAGNI 원칙상 현재 불필요하나, 향후 정리 필요할 수 있음

## HIGH 이슈 (코드 리뷰)

### H1: paymentGateway.confirm() 실패 시 에러 처리 없음
- Toss API 4xx/5xx, 네트워크 타임아웃 시 raw exception이 클라이언트에 전파
- Toss가 실제 승인했으나 응답 타임아웃인 경우 Payment가 PENDING 상태로 남아 불일치 발생
- 에러 catch 후 Payment.fail() 처리 + 의미 있는 에러 메시지 반환 필요

### H2: paymentGateway.cancel() 실패 시 전체 롤백
- Toss cancel API 실패 시 @Transactional rollback → 예약이 CONFIRMED 상태로 유지
- 고객이 취소를 요청했는데 취소가 안 되는 상황
- cancel 실패 시 재시도 로직 또는 수동 처리 플래그 필요

### H3: 읽기 메서드에 @Transactional(readOnly = true) 미적용
- `getMyReservations()`, `getMyReservation()` 등 읽기 전용 메서드에 readOnly 미설정
- 기능 문제는 없으나 Spring 최적화(읽기 전용 트랜잭션) 미활용

### H4: PendingReservationScheduler 개별 처리에 트랜잭션 없음
- 각 만료 예약 처리 시 reservation.save() 성공 후 inventory.release() 실패하면 예약은 취소됐는데 재고 미복원
- 개별 예약 처리를 트랜잭션으로 묶어야 함

### H5: memberId, status + expiresAt 인덱스 누락
- `findByMemberId` — 마이페이지 매 조회 시 full collection scan
- `findExpiredPending` — status + expiresAt 복합 인덱스 없음, 스케줄러 매분 실행
- MongoReservationRepository.createIndexes()에 인덱스 추가 필요

### H6: searchProperties()가 전체 데이터를 메모리에 로드
- `propertyRepository.findAll().filter { it.isBookable() }` — 전체 로드 후 인메모리 필터
- 숙소 수 증가 시 성능 문제, 쿼리 레벨 필터링 필요

### H7: PendingReservationScheduler 패키지 위치
- payment.infrastructure.scheduler에 위치하지만 Reservation, Payment, Inventory 세 도메인을 오케스트레이션
- booking.infrastructure.scheduler 또는 공유 패키지로 이동 고려
