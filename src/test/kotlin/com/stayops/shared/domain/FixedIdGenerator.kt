package com.stayops.shared.domain

/**
 * 테스트용 IdGenerator. 미리 지정한 ID 시퀀스를 순서대로 반환한다.
 *
 * 사용 예:
 * ```kotlin
 * val idGenerator = FixedIdGenerator("rsv-001", "guest-001", "pay-001")
 * val sut = CustomerReservationApplication(..., idGenerator = idGenerator)
 * // 호출 순서대로 "rsv-001", "guest-001", "pay-001" 반환
 * ```
 *
 * 시퀀스가 소진된 후 추가 호출이 일어나면 `NoSuchElementException`이 발생한다.
 * 이는 의도된 동작으로, 테스트가 ID 호출 횟수를 정확히 명시하도록 강제한다.
 */
class FixedIdGenerator(vararg ids: String) : IdGenerator {
    private val iterator = ids.toList().iterator()

    override fun generate(): String = iterator.next()
}
