package com.stayops.shared.config

import com.stayops.shared.time.StayopsTimeProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * `java.time.Clock`을 Spring Bean으로 등록한다.
 *
 * Application/Infrastructure에서 시각이 필요할 때는 시스템 시계를 직접 호출하지 말고
 * 이 Bean을 주입받아 `clock.instant()`, `Instant.now(clock)`, `LocalDate.now(clock)`
 * 등을 사용한다. 테스트에서는 `Clock.fixed(...)` 또는 mutable clock으로 교체하여
 * 시간 시나리오를 결정적으로 제어할 수 있다.
 *
 * 기본 시간대는 설정으로 관리한다. 숙소 현지 날짜 판단이 필요한 경우에는
 * 숙소의 timezone 값을 기준으로 별도 계산한다.
 */
@Configuration
class ClockConfig(
    private val timeProperties: StayopsTimeProperties
) {

    @Bean
    fun clock(): Clock = Clock.system(timeProperties.defaultZone())
}
