# Testcontainers MongoDB 격리 실패 수정

## 문제

`./gradlew test` 실행 시 로컬 Docker MongoDB의 데이터가 삭제됨.
Testcontainers가 별도 컨테이너를 띄우지만, 테스트가 실제로는 로컬 MongoDB(localhost:27017)에 연결되어
`deleteAll()`이 개발 데이터를 파괴.

## 근본 원인

Spring Boot 3 → 4 마이그레이션 시 MongoDB 프로퍼티 키가 변경됨.

| 버전 | 프로퍼티 키 |
|---|---|
| Spring Boot 3.x | `spring.data.mongodb.uri` |
| Spring Boot 4.x | `spring.mongodb.uri` |

`TestcontainersConfiguration`이 이전 키(`spring.data.mongodb.uri`)를 오버라이드하고 있어서
Spring Boot 4가 이를 무시 → `application.yml`의 `spring.mongodb.uri`(localhost:27017)로 연결.

### 영향 범위

21개 통합 테스트가 로컬 MongoDB에 연결되어 `deleteAll()` 실행:
- Repository 테스트 13개 (각 컬렉션 삭제)
- API 통합 테스트 5개
- E2E/동시성 테스트 3개 (BookingE2ETest는 모든 컬렉션 삭제)

## 추가 발견: Replica Set 문제

프로퍼티 키 수정 후 격리에는 성공했으나, E2E 테스트 2개가 실패.

| 환경 | MongoDB 모드 | 트랜잭션 |
|---|---|---|
| docker compose (로컬 개발) | Replica Set (`--replSet rs0`) | 가능 |
| Testcontainers `MongoDBContainer` 2.0.3 | Standalone | 불가 |

E2E 테스트에서 `retryWrites=true`(기본값)로 쓰기 시 트랜잭션 번호가 필요한데,
Standalone에서는 지원하지 않아 `Transaction numbers are only allowed on a replica set member or mongos` 에러 발생.

이전에 E2E가 통과했던 이유: 격리가 안 되어 로컬 Docker MongoDB(Replica Set)에 연결되었기 때문.

## 해결

`MongoDBContainer` 대신 `GenericContainer`로 docker compose와 동일한 Replica Set 환경을 직접 구성.

```kotlin
// 변경 전
fun mongoDbContainer(): MongoDBContainer {
    return MongoDBContainer(DockerImageName.parse("mongo:8"))
}
fun mongoDbProperties(mongo: MongoDBContainer) = DynamicPropertyRegistrar { registry ->
    registry.add("spring.data.mongodb.uri") { mongo.replicaSetUrl }  // 구 키, Standalone
}

// 변경 후
fun mongoDbContainer(): GenericContainer<*> {
    return GenericContainer(DockerImageName.parse("mongo:8"))
        .withExposedPorts(27017)
        .withCommand("--replSet", "rs0")                              // Replica Set 모드
        .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1))
}
fun mongoDbProperties(mongo: GenericContainer<*>) = DynamicPropertyRegistrar { registry ->
    mongo.execInContainer("mongosh", "--eval", "rs.initiate(...)")    // Replica Set 초기화
    registry.add("spring.mongodb.uri") { ... }                       // 신 키, Replica Set
}
```

### 변경 포인트 요약

| 변경 | 이유 |
|---|---|
| `MongoDBContainer` → `GenericContainer` | Testcontainers 2.0.3에서 자동 Replica Set 미지원 |
| `--replSet rs0` + `rs.initiate()` | 프로덕션(docker compose)과 동일한 환경 |
| `spring.data.mongodb.uri` → `spring.mongodb.uri` | Spring Boot 4 프로퍼티 키 변경 대응 |
| `@Qualifier("mongoDbContainer")` | Redis도 GenericContainer라 빈 충돌 방지 |

## 검증

1. 전체 612개 테스트 통과
2. 테스트 실행 전후 로컬 MongoDB 데이터 건수 동일 (격리 확인)
3. E2E 테스트가 Testcontainers Replica Set에서 정상 동작 (트랜잭션 확인)

## 교훈

- Spring Boot 메이저 버전 업그레이드 시 **프로퍼티 키 변경**을 반드시 확인해야 함
- Testcontainers 격리 실패는 **테스트가 통과하기 때문에 발견이 어려움** — 테스트 자체는 `deleteAll()`로 시작하므로 어떤 DB에 연결되든 통과
- 테스트 환경은 **프로덕션과 동일한 조건**(Replica Set)이어야 의미 있는 검증이 됨
