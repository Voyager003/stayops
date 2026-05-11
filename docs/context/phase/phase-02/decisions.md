# Phase 2 설계 결정 및 Q&A

## 1. Property가 테넌트 경계로 유효한가

- **질문**: property가 최상위 테넌트인 것이 유효한가?
- **결론**: 유효
- **근거**: 이 시스템의 요구사항은 "숙소별 독립적인 객실·재고·요금·예약 데이터 운영". 모든 하위 도메인이 `propertyId`로 Property를 참조하며, Property 자신은 아무것도 참조하지 않는다.
- **트레이드오프**: Guest가 `propertyId`로 스코핑되므로 같은 소유주의 여러 숙소를 방문한 고객은 숙소별로 별개 레코드로 관리된다. 크로스 숙소 고객 통합은 현재 요구사항에 없으므로 YAGNI 원칙상 보류.

## 2. propertyId는 MongoDB의 _id인가, 별도 식별자인가

- **질문**: `propertyId`는 MongoDB의 `_id`인가, 아니면 별도 식별자인가?
- **결론**: MongoDB `_id`와 1:1 매핑되는 동일한 값
- **설명**: 도메인 모델의 `id: String`은 인프라 계층 `PropertyDocument`의 `@Id val id: String`을 통해 MongoDB `_id`에 매핑된다. 다른 도메인이 저장하는 `propertyId: String`은 해당 Property Document의 `_id` 값.
- **왜 String인가**: 도메인 모델을 MongoDB에 독립적으로 유지하기 위해 `ObjectId` 타입 대신 `String`을 사용. `domain/` 패키지에는 순수 Kotlin만 허용.

## 3. 스키마 결정 지연 원칙

- **결정**: MongoDB Document 구조와 인덱스는 도메인 모델이 완성·검증된 후 확정
- **근거**: Phase 문서의 인덱스 제안은 참고용. 잘못된 도메인 모델은 수정 비용이 크지만, 인덱스는 나중에 추가해도 늦지 않다.
- **적용**: CLAUDE.md에 "Schema Design Rules" 섹션으로 명문화

## 4. companion object란

- **질문**: `companion object`는 무엇인가?
- **설명**: Kotlin에 `static`이 없기 때문에 그 역할을 하는 클래스 내부의 싱글톤 객체. 인스턴스 없이 호출 가능한 메서드와 프로퍼티를 담는다.
- **이 프로젝트에서의 용도**: 정적 팩토리 메서드(`create`, `of`, `from`)를 담는 용도로만 사용. Java의 `static factory method`와 동일 개념.

## 5. companion object의 JVM 실체

- **질문**: JVM에서 객체가 실제로 어떻게 생성되는가?
- **설명**: Kotlin 컴파일러가 `companion object`를 `static final` 내부 클래스(`Companion`)로 변환하고, 그 싱글톤 인스턴스를 `static` 필드로 보유.
- **호출 흐름**: `Property.create()` → `Property.Companion.create()` → `new Property(...)` → 힙에 인스턴스 할당
- **Java 상호운용**: `@JvmStatic` 없으면 Java에서 `Property.Companion.create()`로 호출해야 함. `@JvmStatic` 추가 시 진짜 static 메서드가 별도 생성되어 `Property.create()`로 호출 가능.

## 6. PropertyDocument의 역할

- **질문**: PropertyDocument의 역할은 무엇인가? Java의 toEntity와 동일한 역할인가?
- **설명**: `PropertyDocument`는 MongoDB와 도메인 모델 사이의 변환을 담당하는 인프라 계층의 객체.
  - `toDomain()`: Document → 도메인 모델 (`Property.reconstitute()` 호출)
  - `from(property)`: 도메인 모델 → Document
- **결론**: Java JPA의 `@Entity`와 `toEntity()` 조합과 역할이 동일하다. 도메인 모델이 MongoDB 어노테이션(`@Document`, `@Id`)에 오염되지 않도록 분리.

## 7. @Transactional 제거 결정

- **결정**: `PropertyApplication`에서 `@Transactional`을 제거
- **원인**: Spring Boot 4.x가 `MongoTransactionManager`를 자동 구성하여, `@Transactional` 사용 시 MongoDB 트랜잭션을 시도한다. Testcontainers standalone MongoDB는 replica set이 아니므로 "Transaction numbers are only allowed on a replica set member or mongos" 오류 발생.
- **근거**: Phase 2의 PropertyApplication은 단일 문서 CRUD만 수행. 단일 문서 연산은 MongoDB 레벨에서 이미 원자적이므로 애플리케이션 트랜잭션이 불필요하다.
- **재도입 시점**: 멀티 도메인 오케스트레이션이 필요한 Phase 8(Reservation)에서 replica set 기반으로 재도입 예정.

## 교훈

- **VO 필드 수**: `address`(5개), `contactInfo`(3개) 필드를 VO로 묶으면 도메인 모델의 필드 수가 줄고 관련 검증 로직도 VO 내부로 이동된다.
- **SUSPENDED 상태**: 문서에 명시되지 않은 전이는 허용하지 않는다. 나중에 요구사항이 생기면 그때 추가.
