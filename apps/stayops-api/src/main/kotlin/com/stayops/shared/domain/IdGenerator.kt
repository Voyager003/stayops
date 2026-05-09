package com.stayops.shared.domain

/**
 * 도메인 식별자 생성 포트.
 *
 * Application 레이어가 ID 생성 전략(UUID, Snowflake, Ulid 등)에 의존하지 않도록
 * 도메인 레이어에서 인터페이스를 정의한다. 테스트에서는 결정적 구현체로 교체하여
 * ID 기반 단언이 가능하도록 한다.
 *
 * 본 인터페이스의 구현체는 절대 예외를 던지지 않으며, 항상 비어 있지 않은
 * 문자열을 반환해야 한다.
 */
interface IdGenerator {
    fun generate(): String
}
