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

## 4. 상태 전이 메서드란

- **질문**: 상태 전이 메서드가 무엇인가?
- **설명**: 도메인 객체의 상태(status)를 변경하는 메서드. `activate()`, `deactivate()`, `suspend()`가 해당.
- **핵심 1**: 전이 가능 여부 검증이 도메인 내부에 있다 — 서비스 계층이 아닌 도메인 모델이 규칙을 소유.
- **핵심 2**: 불변 객체 패턴 — 기존 인스턴스를 수정하지 않고 `copy()`로 새 인스턴스를 반환.
- **허용 전이**: INACTIVE→ACTIVE(`activate`), ACTIVE→INACTIVE(`deactivate`), ACTIVE→SUSPENDED(`suspend`). SUSPENDED에서 나오는 경로는 미정의이므로 전부 예외.

## 5. companion object란

- **질문**: `companion object`는 무엇인가?
- **설명**: Kotlin에 `static`이 없기 때문에 그 역할을 하는 클래스 내부의 싱글톤 객체. 인스턴스 없이 호출 가능한 메서드와 프로퍼티를 담는다.
- **이 프로젝트에서의 용도**: 정적 팩토리 메서드(`create`, `of`, `from`)를 담는 용도로만 사용. Java의 `static factory method`와 동일 개념.

## 6. companion object의 JVM 실체

- **질문**: JVM에서 객체가 실제로 어떻게 생성되는가?
- **설명**: Kotlin 컴파일러가 `companion object`를 `static final` 내부 클래스(`Companion`)로 변환하고, 그 싱글톤 인스턴스를 `static` 필드로 보유.
- **호출 흐름**: `Property.create()` → `Property.Companion.create()` → `new Property(...)` → 힙에 인스턴스 할당
- **Java 상호운용**: `@JvmStatic` 없으면 Java에서 `Property.Companion.create()`로 호출해야 함. `@JvmStatic` 추가 시 진짜 static 메서드가 별도 생성되어 `Property.create()`로 호출 가능.

## 교훈

- **VO 필드 수**: `address`(5개), `contactInfo`(3개) 필드를 VO로 묶으면 도메인 모델의 필드 수가 줄고 관련 검증 로직도 VO 내부로 이동된다.
- **SUSPENDED 상태**: 문서에 명시되지 않은 전이는 허용하지 않는다. 나중에 요구사항이 생기면 그때 추가.
