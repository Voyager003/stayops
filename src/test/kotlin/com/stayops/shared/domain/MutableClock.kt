package com.stayops.shared.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * 테스트용 mutable Clock. 명시적으로 시간을 진행시킬 수 있다.
 *
 * `Clock.fixed()`는 한 번 고정되면 변경 불가능하지만, MutableClock은 `advance()`,
 * `set()` 메서드로 시간을 자유롭게 조작할 수 있어 TTL 만료, 노쇼 자동 전환,
 * 스케줄러 시간 흐름 등을 결정적으로 시뮬레이션할 수 있다.
 *
 * Spring 통합 테스트에서는 `@TestConfiguration`에 `@Primary` Bean으로 등록하여
 * 프로덕션의 SystemClock을 대체한다.
 *
 * 사용 예:
 * ```kotlin
 * val clock = MutableClock(Instant.parse("2026-04-09T10:00:00Z"))
 * // ...
 * clock.advance(Duration.ofMinutes(16))  // 16분 시간 진행
 * clock.instant()  // now()는 이제 10:16
 * ```
 */
class MutableClock(
    initial: Instant,
    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
) : Clock() {

    @Volatile
    private var current: Instant = initial

    override fun instant(): Instant = current

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    fun set(instant: Instant) {
        current = instant
    }

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
