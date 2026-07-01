# Ubiquitous Language 문서

StayOps는 숙소 운영자가 숙소, 객실, 요금, 재고, 채널을 관리하고, 고객이 예약 의사를 생성해 결제를 진행한 뒤, 숙소 운영 흐름에서 예약을 확인하고 투숙을 처리하는 PMS다. 

외부 OTA 채널과는 재고 동기화와 웹훅 예약 수신으로 연결된다.

## Shared Kernel

| 용어 | 의미 | 코드 기준 |
|---|---|---|
| Money | 금액과 통화를 함께 다루는 값. 요금, 결제, 정산, 고객 방문 금액에서 공통으로 사용한다. | `shared.domain.Money` |
| DateRange | 체크인부터 체크아웃 전날까지의 숙박 기간. 재고 차감/hold는 `checkOut` 당일을 제외한 숙박일 단위로 처리한다. | `shared.domain.DateRange` |

## Member Context

| 용어 | 의미 | 상태/행위 |
|---|---|---|
| Member | StayOps에 로그인하는 시스템 사용자. 고객 예약의 고객 정보가 아니라 운영자/관리자 계정의 의미도 포함한다. | 생성 시 `ACTIVE`, 로그인 기록 가능, 비활성화 가능 |
| MemberRole | 시스템 차원의 역할. `ADMIN`은 모든 숙소 접근이 가능하고, 그 외 역할은 `PropertyAccess`로 숙소 접근권을 판단한다. | `Member.hasAccessTo()` |
| PropertyAccess | 특정 Member가 특정 Property에 대해 가진 접근권. 별도 생명주기보다 Member 내부 권한 목록에 가깝다. | `grantAccess`, `revokeAccess` |
| PropertyRole | 숙소 단위 권한. 숙소 온보딩 시 소유자에게 `OWNER` 권한이 부여된다. | `PropertyOnboardingApplication` |
| MemberStatus | 회원 계정의 사용 가능 상태. 현재 비즈니스 로직에서는 `ACTIVE`, `INACTIVE`가 사용된다. | `deactivate()` |

혼동 주의:

- Member는 시스템 사용자다. 숙박하는 사람의 정보는 Reservation의 `GuestInfo`, Guest Context의 `Guest`로 표현한다.

## Property Context

| 용어 | 의미 | 상태/행위 |
|---|---|---|
| Property | 운영자가 관리하는 숙소. 예약 가능 여부, 타임존, 통화, 주소, 연락처를 소유한다. | 생성 시 `INACTIVE` |
| PropertyStatus | 숙소 운영 상태. 예약 검색과 예약 intent 생성에서는 `ACTIVE`인 숙소만 예약 가능하다. | `ACTIVE`, `INACTIVE`, `SUSPENDED` |
| PropertyType | 숙소 유형. 호텔, 모텔, 펜션 등 숙소의 분류다. | 숙소 생성 입력 |
| Address | 숙소 위치 정보. 거리, 도시, 국가, 좌표를 포함한다. | Property 내부 값 |
| ContactInfo | 숙소 연락처. 전화번호, 이메일, 웹사이트를 포함한다. | Property 내부 값 |
| Property Onboarding | 숙소를 등록하고, 기본 DIRECT 채널을 만들며, 소유자에게 숙소 접근권을 부여하는 업무 흐름. | `PropertyOnboardingApplication` |

혼동 주의:

- Property는 판매 가능한 상품 그 자체가 아니라 숙소 운영 단위다. 실제 판매 단위는 RoomType과 RatePlan, RoomInventory 조합으로 만들어진다.

## Room Context

| 용어 | 의미 | 상태/행위 |
|---|---|---|
| RoomType | 고객이 예약하는 객실 상품 단위. 최대 수용 인원, 기본 요금, 편의시설을 가진다. | 객실 검색과 요금 계산의 기준 |
| Room | 실제 객실 한 칸. 객실 번호, 층, 청소/정비/투숙 상태를 가진다. | 체크인 시 배정 가능 |
| RoomStatus | 실물 객실의 운영 상태. 재고 수량과 별개로 객실 운영 상태를 표현한다. | `AVAILABLE`, `OCCUPIED`, `MAINTENANCE`, `CLEANING` |
| Check-in | 확정된 예약에 실제 객실을 배정하고 투숙을 시작하는 행위. | Reservation과 Room 상태가 함께 바뀐다. |
| Check-out | 투숙이 끝나고 객실을 청소 대기 상태로 전환하는 행위. | Guest 방문 기록 갱신 이벤트와 연결 |

혼동 주의:

- RoomType은 예약 가능한 타입이고, Room은 실제 객실이다. 재고는 Room 개별 목록이 아니라 RoomType과 날짜 기준 수량으로 관리한다.

## Inventory Context

| 용어 | 의미 | 상태/행위 |
|---|---|---|
| RoomInventory | 특정 숙소, 객실 타입, 날짜의 판매 가능 수량 상태. | `totalCount`, `reservedCount`, `blockedCount`, `heldCount` |
| Available Count | 현재 판매 가능한 수량. `totalCount - reservedCount - blockedCount - heldCount`로 계산한다. | `availableCount` |
| Reserved Count | 최종 예약으로 소비된 수량. 예약 확정 흐름에서 hold가 소비되면 증가한다. | `reserve`, `consumeHold` |
| Blocked Count | 운영자가 판매하지 않도록 막은 수량. 객실 오픈/마감, 정비, 수동 차단에 해당한다. | `block`, `unblock` |
| Held Count | 결제 진행 중 임시 점유된 수량. 최종 예약이 아니지만 다른 고객의 중복 진행을 막는다. | `hold`, `releaseHold`, `consumeHold` |
| InventoryHold | ReservationIntent가 결제 대기 중인 동안 날짜별 재고를 임시 점유하는 기록. | `HELD`, `PAYMENT_PROCESSING`, `CONSUMED`, `RELEASED`, `EXPIRED` |
| Hold Release | 고객이 결제를 완료하지 못했거나, 결제 승인이 실패했거나, ReservationIntent가 만료되어 임시로 잡아둔 객실을 다시 판매 가능 상태로 돌려놓는 보상 행위. 비즈니스적으로는 "고객에게 아직 판매되지 않은 객실을 시장에 되돌리는 것"이다. | `RoomInventoryHoldApplication.release()` |
| Hold Consume | PG 결제 승인이 성공하고 최종 Reservation을 만들 수 있게 되었을 때, 임시 점유 수량을 실제 예약 수량으로 확정하는 행위. 비즈니스적으로는 "결제 중이라 잠시 잡아둔 객실을 판매 완료 객실로 전환하는 것"이다. | `RoomInventoryHoldApplication.consume()` |

Hold 생명주기:

1. 고객이 결제 단계로 진입하면 숙박일별 `heldCount`가 증가한다.
2. 결제가 성공하면 `heldCount`가 감소하고 `reservedCount`가 증가한다. 이때 hold는 `CONSUMED`가 된다.
3. 결제가 실패하거나 intent가 만료되면 `heldCount`만 감소한다. 이때 hold는 `RELEASED` 또는 `EXPIRED`가 된다.
4. release와 consume은 모두 중복 판매를 막기 위한 재고 복구/확정 행위이며, 고객에게 아직 확정되지 않은 재고와 이미 판매된 재고를 구분하기 위해 필요하다.

혼동 주의:

- Hold는 최종 예약이 X. 고객이 결제 중인 좌석을 잠시 잡아두는 개념.

## Rate Context

| 용어 | 의미 | 상태/행위 |
|---|---|---|
| RatePlan | 객실 타입의 판매 요금 정책. 기간, 요일, 채널 조건, 우선순위, 가격을 가진다. | 생성 시 `ACTIVE` |
| RatePlanType | 요금 정책의 적용 방식. 시즌, 요일, 채널 전용, 특별 요금 등으로 구분한다. | `SEASONAL`, `WEEKDAY`, `CHANNEL_SPECIFIC`, `SPECIAL` |
| DayOfWeekRate | 특정 요일에 적용되는 요금. 기본 요금보다 우선 적용될 수 있다. | `RatePlan.priceForDate()` |
| Channel-specific Rate | 특정 채널 코드에만 적용되는 요금. | `CHANNEL_SPECIFIC` |
| RateResolver | 여러 RatePlan 중 날짜와 채널에 맞는 요금을 결정하는 도메인 서비스. | `RateResolverService` |

혼동 주의:

- RoomType의 `basePrice`는 기본값이고, 최종 예약 금액은 RatePlan과 수수료 계산을 거쳐 `ReservationPricing`에 스냅샷으로 저장된다.

## Reservation Context

| 용어 | 의미 | 상태/행위 |
|---|---|---|
| ReservationIntent | 고객이 결제를 시작하기 위해 예약 조건, 결제, 재고 hold를 묶어둔 임시 예약 의사. | 생성 시 `PAYMENT_WAITING` |
| Reservation | 결제 승인 이후 생성되는 숙소 운영 기준 예약. 객실 배정, 체크인, 체크아웃, 취소, 노쇼의 기준이 된다. | 생성 시 `PENDING` |
| GuestInfo | 예약 시점에 입력된 투숙객 정보 스냅샷. Guest master가 바뀌어도 예약 이력의 고객 정보는 보존된다. | Reservation/Intent 내부 값 |
| ReservationPricing | 예약 시점의 금액 스냅샷. 객실 요금, 추가금, 수수료, 총액, 순매출을 포함한다. | Reservation/Intent 내부 값 |
| ReservationChannel | 예약이 들어온 채널 정보. DIRECT 또는 OTA 채널 코드와 수수료율을 담는다. | Reservation/Intent 내부 값 |
| Payment Waiting | 고객이 결제를 완료하기 전 대기 상태. 이 동안 hold와 intent가 만료될 수 있다. | `ReservationIntentStatus.PAYMENT_WAITING` |
| Confirm Requested | 고객 결제 성공 redirect 이후 서버가 PG 승인 처리를 outbox에 맡긴 상태. | `ReservationIntentStatus.CONFIRM_REQUESTED` |
| Reserved Intent | 결제 승인과 Reservation 생성이 끝나 ReservationIntent가 최종 Reservation에 연결된 상태. | `ReservationIntentStatus.RESERVED` |
| Pending Reservation | 결제 승인 후 생성되었지만 숙소 운영자가 아직 확인하지 않은 예약. | `ReservationStatus.PENDING` |
| Confirmed Reservation | 숙소 운영자가 확인하여 실제 투숙 운영 대상으로 확정한 예약. | `ReservationStatus.CONFIRMED` |
| Checked-in Reservation | 투숙이 시작된 예약. | `ReservationStatus.CHECKED_IN` |
| Checked-out Reservation | 투숙이 종료된 예약. | `ReservationStatus.CHECKED_OUT` |
| Cancelled Reservation | 취소된 예약. `PENDING` 예약 취소와 `CONFIRMED` 예약 취소 모두 가능하다. | `ReservationStatus.CANCELLED` |
| No-show | 확정 예약 고객이 오지 않은 상태. | `ReservationStatus.NO_SHOW` |

핵심 흐름:

1. 고객이 숙소, 객실 타입, 날짜, 인원을 선택한다.
2. 서버가 가용 재고와 요금을 확인하고 ReservationIntent를 만든다.
3. InventoryHold가 생성되어 숙박일별 재고를 임시 점유한다.
4. Payment가 `PENDING`으로 생성되고 클라이언트가 PG 결제를 진행한다.
5. 결제 승인 요청이 접수되면 intent와 payment가 승인 요청 상태로 전환되고 PaymentOutbox가 생성된다.
6. Outbox worker가 PG 승인을 확인한다.
7. 승인 성공 시 Guest를 찾거나 생성하고, Reservation을 `PENDING`으로 생성하고, hold를 소비한다.
8. 숙소 운영자가 확인하면 Reservation이 `CONFIRMED`가 된다.

혼동 주의:

- ReservationIntent는 예약 전 단계의 임시 의사이며, Reservation은 결제 승인 후 생성되는 운영 예약이다.
- Reservation의 `PENDING`은 결제 대기가 아니라 숙소 운영자 확인 대기다. 결제 대기는 ReservationIntent와 Payment 상태에서 표현한다.

## Payment Context

| 용어 | 의미 | 상태/행위 |
|---|---|---|
| Payment | 결제 주문과 PG 승인/실패/취소 상태를 표현하는 결제 Aggregate. | 생성 시 `PENDING` |
| Order ID | PG 승인 요청에 사용하는 주문 식별자. ReservationIntent 또는 Reservation 식별자를 포함해 생성된다. | `STAYOPS-{id}-{timestamp}` |
| Payment Key | PG가 결제 건을 식별하는 키. 로그에는 suffix만 남긴다. | `paymentKey` |
| Confirm Payment | PG에 결제 승인을 요청하는 행위. | `requestConfirm`, `approve` |
| PaymentOutboxMessage | PG 승인/취소를 비동기로 처리하기 위한 작업 메시지. 재시도, lease, idempotency key를 가진다. | `CONFIRM_PAYMENT`, `CANCEL_PAYMENT` |
| PaymentStatus.PENDING | 결제 주문은 만들어졌지만 승인 요청 전 상태. | 결제창 진입 전/진입 중 |
| PaymentStatus.CONFIRM_REQUESTED | 승인 요청이 접수되어 outbox worker가 처리할 상태. | 고객 결제 성공 redirect 이후 |
| PaymentStatus.APPROVED | PG 승인 성공 상태. | Reservation 생성/연결 가능 |
| PaymentStatus.FAILED | PG 승인 실패 또는 요청 불일치로 결제가 실패한 상태. | hold release 대상 |
| PaymentStatus.CANCEL_REQUESTED | 승인된 결제의 취소 요청이 접수된 상태. | outbox 취소 처리 대상 |
| PaymentStatus.CANCELLED | 결제 취소가 완료된 상태. | 환불 완료 |
| PaymentStatus.CANCEL_FAILED | 결제 취소가 실패한 상태. | 운영자 개입 필요 |

혼동 주의:

- PaymentOutbox의 `COMPLETED`는 outbox 작업 처리가 끝났다는 뜻이고, 결제 자체가 항상 성공했다는 뜻은 X. 결제 성공 여부는 PaymentStatus로 판단한다.

## Channel Context

Channel Context는 PMS가 외부 판매 채널과 연결되는 경계를 표현한다. 숙소 운영자는 PMS에서 객실, 요금, 재고를 관리하지만 실제 판매는 자사 예약 사이트와 OTA 같은 여러 채널에서 동시에 발생한다. 

따라서 Channel Context의 존재 의의는 다음 세 가지다.

1. PMS 내부 모델과 외부 채널 모델 사이의 언어를 분리
2. PMS 재고 변경을 외부 채널로 발행
3. 외부 채널에서 발생한 예약/취소를 PMS 예약과 재고로 반영

| 용어 | 의미 | 상태/행위 |
|---|---|---|
| Channel | 예약 유입 또는 재고 발행 대상 채널. DIRECT와 OTA를 포함한다. | `ACTIVE`, `INACTIVE`, `SUSPENDED` |
| DIRECT Channel | StayOps 자사 예약 사이트 채널. 숙소 온보딩 시 기본 생성된다. | code=`DIRECT` |
| OTA Channel | 외부 온라인 여행사 채널. API endpoint와 수수료율을 가진다. | `ChannelType.OTA` |
| ChannelMapping | PMS 내부 식별자와 OTA 외부 코드를 연결하는 매핑. 현재는 webhook 수신에서 부분 사용한다. | `findInternalId`, `findExternalCode` |
| MappingEntry | 하나의 내부 ID와 외부 코드 대응 관계. | `MappingType`별 중복 방지 |
| ARI Push | Availability, Rate, Inventory 정보를 외부 채널로 발행하는 작업. 현재 코드는 가용 재고 push 중심이다. | `SyncTaskType.AVAILABILITY_UPDATE` |
| SyncTask | OTA로 발행할 작업을 저장하고 재시도하는 채널 동기화 작업. | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `SKIPPED`, `FAILED` |
| ProcessedWebhookEvent | 이미 처리한 웹훅 이벤트를 기록해 중복 처리를 막는 멱등성 기록. | Channel webhook flow |
| Webhook Signature | 외부 웹훅 요청이 신뢰 가능한 채널에서 왔는지 검증하는 서명. | `WebhookSignatureVerifier` |

프로덕션 OTA/채널매니저와 현재 프로젝트의 차이:

| 구분 | 실제 프로덕션 OTA/채널매니저 | 현재 StayOps 프로젝트 |
|---|---|---|
| 연결 주체 | OTA와 계약/인증을 완료한 Connectivity Partner 또는 Channel Manager가 숙소의 예약, 요금, 가용 재고를 연동한다. | Mock OTA 서버와 PMS가 같은 프로젝트 안에서 시뮬레이션된다. |
| 상품 식별자 | OTA별 hotel id, room id, rate id 같은 외부 코드가 존재하고 PMS 내부 ID와 매핑해야 한다. | Mock OTA는 대부분 PMS의 `roomTypeId`를 그대로 사용하며, `ChannelMapping`은 웹훅 수신에서 부분적으로만 사용한다. |
| 재고/요금 발행 | 보통 ARI(Availability, Rates, Inventory)를 일정 기간 단위로 push하거나 OTA API에 갱신한다. | 현재는 재고 변경 이벤트에서 `AVAILABILITY_UPDATE` SyncTask를 만들고, 가용 객실 수 중심으로 push한다. |
| 예약 수신 | OTA에서 예약/취소가 발생하면 파트너 시스템이 reservation API 조회, notification, webhook 등으로 수신해 PMS 예약으로 변환한다. | `ChannelWebhookApi`가 `BOOKING`, `CANCELLATION` 이벤트를 받고 `WebhookApplication`이 Reservation과 재고를 직접 반영한다. |
| 결제 처리 | OTA 예약은 OTA 선결제, 현장결제, 카드 보증 등 채널 정책에 따라 결제 책임이 다르다. | OTA 예약은 외부에서 이미 승인된 결제로 보고 `createApprovedExternalPayment()`로 기록한다. |
| 운영 안정성 | 인증, rate limit, 재처리, unmapped room/rate, fallback reservation, 장애 복구가 중요하다. | 서명 검증, 멱등성 기록, SyncTask 재시도/lease를 갖췄지만 실제 OTA별 인증/스키마/요금 push는 단순화되어 있다. |

Webhook의 의미:

Webhook은 외부 채널에서 이미 발생한 사건을 PMS 내부 예약/재고 언어로 번역하는 수신 경계다. 현재 코드에서 `ChannelWebhookApi`는 `propertyId`, `channelCode`, `X-Webhook-Signature`, raw body를 받고, `WebhookApplication`은 다음 순서로 처리한다.

1. 채널이 존재하는지 확인한다.
2. Webhook signature를 검증한다.
3. `ProcessedWebhookEvent`로 eventId 중복 처리를 막는다.
4. `ChannelMapping`이 있으면 외부 `roomTypeCode`를 내부 `roomTypeId`로 변환하고, 없으면 Mock OTA처럼 전달된 값을 그대로 사용한다.
5. `BOOKING` 이벤트면 재고를 차감하고, Guest를 찾거나 생성하고, OTA 예약을 `CONFIRMED` Reservation으로 생성한다.
6. OTA 결제는 외부에서 처리된 것으로 보고 승인 완료 결제를 기록한다.
7. `CANCELLATION` 이벤트면 기존 OTA 예약을 찾아 취소하고 재고를 복원한다.
8. 예약 생성/취소 이벤트를 발행해 다른 채널로 재고 동기화가 이어질 수 있게 한다.

비즈니스적으로 Webhook은 "외부에서 판매가 일어났음을 PMS가 뒤늦게 알게 되는 입구"다. 고객이 StayOps 자사 예약 화면에서 결제하는 흐름과 달리, OTA 예약은 외부 채널에서 먼저 확정되고 PMS가 이를 반영한다. 

그래서 Webhook에는 결제창, ReservationIntent, InventoryHold가 등장하지 않고, 외부 예약을 내부 Reservation과 Payment 기록으로 변환하는 책임이 있다.

SyncTask의 의미:

SyncTask는 PMS 내부 재고 변경을 외부 채널에 안전하게 발행하기 위한 작업 단위다. 예약 생성, 예약 취소, 재고 차단/해제처럼 가용 객실 수가 바뀌면 외부 OTA도 같은 수량을 알아야 한다. 이때 외부 API 호출을 업무 트랜잭션 안에서 즉시 끝내려 하면 OTA 장애, 네트워크 지연, rate limit 때문에 예약 처리 자체가 흔들릴 수 있다. 그래서 현재 코드는 `ChannelSyncApplication.requestAvailabilitySync()`에서 OTA 채널별 `SyncTask`를 저장하고, worker가 나중에 claim해서 push한다.

SyncTask 상태의 비즈니스 의미:

| 상태 | 의미 |
|---|---|
| `PENDING` | 외부 채널에 아직 발행되지 않은 동기화 요청이다. |
| `IN_PROGRESS` | 특정 worker가 lease를 잡고 외부 채널에 발행 중이다. |
| `COMPLETED` | 외부 채널에 성공적으로 반영된 작업이다. |
| `SKIPPED` | 채널 설정 누락처럼 재시도해도 의미가 없는 작업이다. |
| `FAILED` | 재시도 한도를 넘겨 운영자 확인이 필요한 작업이다. |

비즈니스적으로 SyncTask는 "PMS가 알고 있는 최신 가용 재고를 OTA에도 전파해야 한다"는 약속을 저장한 기록이다.

혼동 주의:

- ChannelMapping은 현재 전체 채널 동기화에 완전 적용된 기능이 아니다. 실제 OTA 연동을 고려해 열어둔 모델이며, 런타임에서는 웹훅 예약 수신 시 외부 room type code를 내부 ID로 복원하는 데 부분 사용한다.
- SyncTask는 예약이 아니라 채널 발행 작업이다. 실패해도 예약 상태와 동일하게 해석하면 안 된다.

## Guest Context

| 용어 | 의미 | 상태/행위 |
|---|---|---|
| Guest | 숙소별 고객 master. 전화번호 기준으로 기존 고객을 찾고, 방문 이력과 등급을 누적한다. | 예약 확정 흐름에서 조회/생성 |
| GuestInfo | 예약 시점에 저장되는 투숙객 스냅샷. Guest master와 다르다. | Reservation Context |
| VisitSummary | 총 방문 수, 총 지출, 총 숙박일, 마지막 방문일을 누적한 값. | `recordVisit()` |
| GuestTier | 방문 수와 지출액으로 산정하는 고객 등급. | `NEW`, `BRONZE`, `SILVER`, `GOLD`, `VIP` |
| Record Visit | 체크아웃 이벤트를 통해 고객 방문 이력을 반영하는 행위. | `GuestEventHandler` |

혼동 주의:

- Guest는 숙소의 고객 관리 대상이고, Member는 시스템 로그인 사용자다.

## Settlement, Statistics, Dashboard Context

| 용어 | 의미 | 상태/행위 |
|---|---|---|
| Settlement | 예약/결제 결과를 기반으로 채널별 매출, 수수료, 순매출을 조회하는 정산 read model. | `SettlementQueryReader` |
| Statistics | 예약 데이터 기반의 채널, 객실, 취소, 매출 통계를 조회하는 read model. | `StatisticsQueryReader` |
| Dashboard | 숙소 운영자가 보는 당일 운영 요약 read model. | `DashboardApplication` |
| Read Model | Aggregate를 직접 소유하지 않고 다른 Context의 데이터를 조회/집계해 화면에 맞춘 결과를 제공하는 모델. | 조회 전용 |

혼동 주의:

- 정산/통계/대시보드는 현재 비즈니스 상태를 변경하는 도메인 모델이 아니라 조회 모델이다. 용어는 운영 리포트 관점에서만 사용한다.

## 현재 코드 기준 근거

- 예약 intent 생성: `apps/stayops-api/src/main/kotlin/com/stayops/reservation/application/service/CustomerReservationIntentCreationApplication.kt`
- 결제 승인 outbox 처리와 예약 생성: `apps/stayops-api/src/main/kotlin/com/stayops/reservation/application/service/ReservationPaymentOutboxApplication.kt`
- 예약 상태 전이: `apps/stayops-api/src/main/kotlin/com/stayops/reservation/domain/model/Reservation.kt`
- 예약 intent 상태 전이: `apps/stayops-api/src/main/kotlin/com/stayops/reservation/domain/model/ReservationIntent.kt`
- 재고 수량 규칙: `apps/stayops-api/src/main/kotlin/com/stayops/inventory/domain/model/RoomInventory.kt`
- 재고 hold 상태 전이: `apps/stayops-api/src/main/kotlin/com/stayops/inventory/domain/model/InventoryHold.kt`
- 결제 상태 전이: `apps/stayops-api/src/main/kotlin/com/stayops/payment/domain/model/Payment.kt`
- 채널 동기화 작업: `apps/stayops-api/src/main/kotlin/com/stayops/channel/domain/model/SyncTask.kt`
- 채널 매핑 맥락: `apps/stayops-api/src/main/kotlin/com/stayops/channel/domain/model/ChannelMapping.kt`
- 채널 웹훅 수신: `apps/stayops-api/src/main/kotlin/com/stayops/channel/api/ChannelWebhookApi.kt`
- 채널 웹훅 처리: `apps/stayops-api/src/main/kotlin/com/stayops/channel/application/service/WebhookApplication.kt`
- 채널 가용 재고 발행 요청: `apps/stayops-api/src/main/kotlin/com/stayops/channel/application/service/ChannelSyncApplication.kt`
- 숙소 온보딩: `apps/stayops-api/src/main/kotlin/com/stayops/property/application/service/PropertyOnboardingApplication.kt`

## 유지보수 규칙

- 새 상태값을 추가하면 해당 Context의 상태 용어와 전이 가능 조건을 이 문서에 추가한다.
- 같은 단어가 두 Context에서 다르게 쓰이면 혼동 주의 항목에 명시한다.
- 구현 편의 용어보다 업무에서 쓰는 말을 우선한다. 예: `outbox row`보다 `PaymentOutboxMessage`, `재고 임시 점유`보다 `InventoryHold`.
- 문서가 코드와 달라지면 코드 기준으로 확인한 뒤 문서를 고친다. 단, 문서가 의도한 비즈니스 언어이고 코드가 어긋난 경우에는 코드 수정 이슈로 분리한다.
