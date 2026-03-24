# CI 테스트 실패 포스트모템

## 문제

GitHub Actions CI에서 통합 테스트가 `MongoTimeoutException`으로 실패.
로컬에서는 동일한 테스트가 항상 통과.

## 근본 원인

`application.yml`이 `.gitignore`에 포함되어 CI runner에 존재하지 않았음.

- 로컬: `docker-compose`의 MongoDB(`localhost:27017`)가 항상 실행 중 + `application.yml` 존재 → 테스트 통과
- CI: `application.yml` 부재 → Spring이 필수 프로퍼티(`toss.payments.secret-key` 등)를 찾지 못함 → context 로드 실패 → `MongoTimeoutException`

## 시도한 접근과 실패 이유

| # | 방법 | 실패 이유 |
|---|------|----------|
| 1 | `application-test.yml` + `spring.profiles.active=test` | 프로파일 활성화됐지만 Testcontainers 프로퍼티 주입 실패 |
| 2 | `DynamicPropertyRegistrar` + `apply { start() }` | `BeanFactoryPostProcessor` 단계에서 `TestcontainersLifecycleBeanPostProcessor` 미등록 |
| 3 | `@DynamicPropertySource` + 싱글톤 컨테이너 | 상위 클래스 companion object의 `@DynamicPropertySource`를 Spring이 감지하지 못함 |
| 4 | `ApplicationContextInitializer` | 하위 클래스의 `@SpringBootTest`가 상위 클래스의 `@ContextConfiguration(initializers)` 덮어씀 |
| 5 | `System.setProperty` | CI 환경에서 시스템 프로퍼티가 Spring에 전달되지 않음 (원인 불명) |
| 6 | CI에서 `docker compose up -d` + 환경변수 주입 | 성공했으나 불필요한 우회 |

## 최종 해결

`application.yml`을 `.gitignore`에서 제외하고 버전 관리 대상에 포함.

파일에 시크릿이 포함되어 있다고 가정했으나, 실제로는 `localhost` 접속 정보와 환경변수 참조(`${TOSS_SECRET_KEY:test_sk_default}`)만 존재. 시크릿은 `application-secret.yml`(gitignored)과 EC2 `.env`에만 존재.

## 교훈

1. **`.gitignore` 대상을 점검하라** — 시크릿이 없는 설정 파일까지 gitignore하면 CI/CD에서 예상치 못한 실패가 발생한다.
2. **로컬에서 통과하는 이유를 의심하라** — 로컬 환경의 `docker-compose` MongoDB가 Testcontainers 프로퍼티 주입 실패를 숨기고 있었다.
3. **단순한 해결책을 먼저 시도하라** — Spring 프로퍼티 주입 메커니즘을 5번 바꾸기 전에, `application.yml`의 내용을 확인하고 커밋 가능 여부를 판단했어야 했다.
