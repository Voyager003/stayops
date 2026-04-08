package com.stayops.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/**
 * `java.time.Clock`을 Spring Bean으로 등록한다.
 *
 * Application/Infrastructure에서 시각이 필요할 때는 시스템 시계를 직접 호출하지 말고
 * 이 Bean을 주입받아 `clock.instant()`, `Instant.now(clock)`, `LocalDate.now(clock)`
 * 등을 사용한다. 테스트에서는 `Clock.fixed(...)` 또는 mutable clock으로 교체하여
 * 시간 시나리오를 결정적으로 제어할 수 있다.
 *
 * 시간대는 한국 단일 운영을 가정하여 `Asia/Seoul`로 고정한다. 글로벌 확장 시
 * 별도 정책으로 검토한다.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
