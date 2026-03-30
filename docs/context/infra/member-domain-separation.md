# Member 도메인 분리 검토

## 현재 구조

`Member` 하나의 도메인이 OWNER(숙소 운영자), CUSTOMER(고객), ADMIN(시스템 관리자)을 모두 포함.
`MemberRole` enum으로 역할을 구분하고 있으나, 사용 맥락이 완전히 다름.

| | OWNER | CUSTOMER |
|---|---|---|
| 사용 시스템 | PMS (`/api/v1/properties/...`) | 예약 사이트 (`/api/v1/booking/...`) |
| 인증 API | `AuthService` | `CustomerAuthService` |
| propertyAccess | 사용 (숙소별 권한) | 사용 안 함 |
| 관심사 | 숙소 관리, 정산, 채널 | 예약, 결제, 마이페이지 |

컨트롤러와 서비스는 이미 분리되어 있으나, 도메인 모델(Member)은 공유 중.

## 분리 판단 기준

| 기준 | 현재 상태 |
|---|---|
| 변경 이유가 다른가 | OWNER는 PMS 요구사항, CUSTOMER는 예약 UX → 다름 |
| 생명주기가 다른가 | OWNER는 사업자 등록, CUSTOMER는 예약 시 생성 → 다름 |
| 필드가 다른가 | OWNER는 propertyAccess, CUSTOMER는 예약 이력/등급 → 다름 |
| 독립 배포 가능성 | PMS와 예약 사이트를 별도 서비스로 분리할 가능성 있음 |

## 분리 시점

현재 규모에서는 분리 불필요. 다음 조건이 발생하면 분리 검토:
- CUSTOMER에 고유 비즈니스 로직 추가 (적립금, 등급 승격 규칙 등)
- OWNER에 고유 필드 추가 (사업자 정보, 정산 계좌 등)
- PMS와 예약 사이트를 별도 서비스로 분리할 때

## 분리 시 구조안

```
현재:
  auth/domain/model/Member.kt  ← OWNER + CUSTOMER + ADMIN 전부

분리 후:
  auth/domain/model/Member.kt       ← 인증 공통 (id, email, passwordHash, status)
  property/domain/model/Operator.kt ← 숙소 운영자 (propertyAccess, 정산 정보)
  booking/domain/model/Customer.kt  ← 고객 (예약 이력, 등급, 적립금)
```
