# Phase 11: Customer Booking (Finestay) — Context

## 진행 상황

| Sub-step | 내용 | 상태 | 커밋 |
|----------|------|------|------|
| 11-1 | Payment 도메인 모델 + 단위 테스트 | 완료 | `d4a745f` |
| 11-2 | MemberRole.CUSTOMER + 고객 인증 서비스 | 완료 | `8bf237e` |
| 11-3 | Security Config + 고객 인증 API | 완료 | `342d12c` |
| 11-4 | 숙소 검색 API (Public) | 완료 | `28ea3d8` |
| 11-5 | Reservation 도메인 변경 (memberId, expiresAt) | 완료 | `f190bb0` |
| 11-6 | Payment 인프라 (MongoDB + Toss Client) | 완료 | 미커밋 |
| 11-7 | BookingApplication (핵심 오케스트레이션) | 미진행 | |
| 11-8 | Booking API + MyPage API | 미진행 | |
| 11-9 | PENDING TTL 스케줄러 | 미진행 | |
| 11-10 | E2E 통합 테스트 | 미진행 | |

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

### C2: OWNER / MANAGER role 구분 없음
- `MemberRole.OWNER`와 `MemberRole.MANAGER`가 코드상 동일하게 동작
- `PropertyRole.OWNER`와 `PropertyRole.MANAGER`를 분기하는 로직 없음
- YAGNI 원칙상 현재 불필요하나, 향후 정리 필요할 수 있음
