package com.qonversion.android.sdk.internal.user.generator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class UserIdGeneratorTest {

    private val generator: UserIdGenerator = UserIdGeneratorImpl()

    @Test
    fun `format check`() {
        val ids = (1..10).map {
            // given
            val regex = Regex("""^QON_[a-zA-Z\d]{32}$""")

            // when
            val id = generator.generate()

            // then
            assertThat(regex.matches(id)).isTrue
            id
        }
        // check for duplicates
        assertThat(ids.size).isEqualTo(ids.toSet().size)
    }
}