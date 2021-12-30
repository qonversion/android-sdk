package com.qonversion.android.sdk.internal.cache

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private typealias CacheType = String

internal class CacheHolderTest {

    private lateinit var cacheHolder: CacheHolder<CachedObject<CacheType>?>
    private val mockInitializer = mockk<Initializer<CachedObject<CacheType>>>()
    private val testKey = "test key"

    @BeforeEach
    fun setUp() {
        cacheHolder = CacheHolder(mockInitializer)
    }

    @Test
    fun `contains requested value`() {
        // given
        val existingValue = mockk<CachedObject<CacheType>>()
        cacheHolder[testKey] = existingValue

        // when
        val result = cacheHolder[testKey]

        // then
        assertThat(result).isEqualTo(existingValue)
        verify(exactly = 0) { mockInitializer.invoke(any()) }
    }

    @Test
    fun `contains null value`() {
        // given
        cacheHolder[testKey] = null

        // when
        val result = cacheHolder[testKey]

        // then
        assertThat(result).isNull()
        verify(exactly = 0) { mockInitializer.invoke(any()) }
    }

    @Test
    fun `does not contain requested value`() {
        // given
        val initializingValue = mockk<CachedObject<CacheType>>()
        every { mockInitializer.invoke(testKey) } returns initializingValue

        // when
        val result = cacheHolder[testKey]

        // then
        assertThat(result).isEqualTo(initializingValue)
        verify(exactly = 1) { mockInitializer.invoke(testKey) }
    }
}