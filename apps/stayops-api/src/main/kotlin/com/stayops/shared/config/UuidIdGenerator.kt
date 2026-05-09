package com.stayops.shared.config

import com.stayops.shared.domain.IdGenerator
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * UUID v4 기반 IdGenerator 프로덕션 구현체.
 *
 * 도메인 식별자가 아직 분산 시스템 정렬·시간 정보를 요구하지 않으므로 UUID v4로
 * 충분하다. 향후 Snowflake/Ulid 등으로 교체가 필요해지면 별도 구현체를 추가하고
 * `@Primary`로 교체할 수 있다.
 */
@Component
class UuidIdGenerator : IdGenerator {
    override fun generate(): String = UUID.randomUUID().toString()
}
