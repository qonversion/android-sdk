package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.dto.CacheLifetime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class InternalCacheLifetimeTest {

    @Nested
    inner class FromCacheLifeTimeTest {

        @Test
        fun `all cases`() {
            // given
            val conformity = mapOf(
                CacheLifetime.OneDay to InternalCacheLifetime.OneDay,
                CacheLifetime.TwoDays to InternalCacheLifetime.TwoDays,
                CacheLifetime.ThreeDays to InternalCacheLifetime.ThreeDays,
                CacheLifetime.Week to InternalCacheLifetime.Week,
                CacheLifetime.TwoWeeks to InternalCacheLifetime.TwoWeeks,
                CacheLifetime.Month to InternalCacheLifetime.Month
            )

            conformity.forEach { (cacheLifeTime, expectedInternalCacheLifeTime) ->
                // when
                val res = InternalCacheLifetime.from(cacheLifeTime)

                // then
                assertThat(res).isEqualTo(expectedInternalCacheLifeTime)
            }
        }
    }
}