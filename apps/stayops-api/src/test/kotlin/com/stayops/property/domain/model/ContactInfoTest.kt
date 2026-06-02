package com.stayops.property.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ContactInfoTest {

    @Test
    fun `웹사이트는 http와 https URL만 허용한다`() {
        assertThat(ContactInfo.of("02-1234-5678", "hotel@example.com", "https://example.com").website)
            .isEqualTo("https://example.com")
        assertThat(ContactInfo.of("02-1234-5678", "hotel@example.com", "http://example.com").website)
            .isEqualTo("http://example.com")
    }

    @Test
    fun `웹사이트가 javascript URL이면 예외가 발생한다`() {
        assertThatThrownBy {
            ContactInfo.of("02-1234-5678", "hotel@example.com", "javascript:alert(1)")
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("website는 http 또는 https URL이어야 합니다")
    }

    @Test
    fun `웹사이트가 비어 있으면 null로 정규화한다`() {
        assertThat(ContactInfo.of("02-1234-5678", "hotel@example.com", " ").website)
            .isNull()
    }
}
