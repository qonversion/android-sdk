package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.cache.mapper.CacheMapper
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.utils.MS_IN_SEC
import io.mockk.called
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

private typealias CacheObjectType = String

internal class CacherTest {

    private lateinit var cacher: CacherImpl<CacheObjectType>
    private val mockCacheMapper = mockk<CacheMapper<CacheObjectType>>()
    private val mockLocalStorage = mockk<LocalStorage>()
    private val mockAppLifecycleObserver = mockk<AppLifecycleObserver>()
    private val mockBackgroundCacheLifetime = mockk<CacheLifetime>()
    private val mockForegroundCacheLifetime = mockk<InternalCacheLifetime>()

    private val testCachingKey: String = "test caching key"
    private val testCachingValue: CacheObjectType = "test caching value"

    @BeforeEach
    fun setUp() {
        cacher = CacherImpl(
            mockCacheMapper,
            mockLocalStorage,
            mockAppLifecycleObserver,
            mockBackgroundCacheLifetime,
            mockForegroundCacheLifetime
        )
    }

    @Nested
    inner class StoreTest {

        private val slotCachedObject = slot<CachedObject<CacheObjectType>>()
        private val mappedObject = "mapped object"
        private val timeEpsilonMs = 100L

        @BeforeEach
        fun setUp() {
            every { mockCacheMapper.toSerializedString(capture(slotCachedObject)) } returns mappedObject
            every { mockLocalStorage.putString(any(), mappedObject) } just runs
        }

        @Test
        fun `store successful`() {
            // given
            val currentTime = Calendar.getInstance().time

            // when
            cacher.store(testCachingKey, testCachingValue)

            // then
            verify(exactly = 1) {
                mockCacheMapper.toSerializedString(any())
                mockLocalStorage.putString(testCachingKey, mappedObject)
            }
            assertThat(slotCachedObject.captured.value).isSameAs(testCachingValue)
            assertThat(slotCachedObject.captured.date.time - currentTime.time).isLessThanOrEqualTo(timeEpsilonMs)
            assertThat(cacher.cachedObjects[testCachingKey]).isSameAs(slotCachedObject.captured)
        }

        @Test
        fun `mapper throws exception`() {
            // given
            val exception = QonversionException(ErrorCode.Serialization)
            every { mockCacheMapper.toSerializedString(any()) } throws exception

            // when
            val e = assertThatQonversionExceptionThrown {
                cacher.store(testCachingKey, testCachingValue)
            }

            // then
            assertThat(e).isSameAs(exception)
            verify(exactly = 1) { mockCacheMapper.toSerializedString(any()) }
            verify(exactly = 0) { mockLocalStorage.putString(testCachingKey, mappedObject) }
            assertThat(cacher.cachedObjects.containsKey(testCachingKey)).isFalse
        }
    }

    @Nested
    inner class LoadTest {

        private val testStoredValue = "test stored value"
        private val mockCachedObject = mockk<CachedObject<CacheObjectType>>()

        @Test
        fun `load successful`() {
            // given
            every { mockLocalStorage.getString(testCachingKey) } returns testStoredValue
            every { mockCacheMapper.fromSerializedString(testStoredValue) } returns mockCachedObject

            // when
            val result = cacher.load(testCachingKey)

            // then
            assertThat(result).isSameAs(mockCachedObject)
            verify(exactly = 1) {
                mockLocalStorage.getString(testCachingKey)
                mockCacheMapper.fromSerializedString(testStoredValue)
            }
        }

        @Test
        fun `load null`() {
            // given
            every { mockLocalStorage.getString(testCachingKey) } returns null

            // when
            val result = cacher.load(testCachingKey)

            // then
            assertThat(result).isNull()
            verify(exactly = 1) { mockLocalStorage.getString(testCachingKey) }
            verify(exactly = 0) { mockCacheMapper.fromSerializedString(any()) }
        }
    }

    @Nested
    inner class IsActualTest {

        @Test
        fun `all tests`() {
            listOf(
                listOf(100L, 10L, 5L, false, true), // cache is actual for foreground
                listOf(100L, 10L, 15L, true, true), // cache is actual for background
                listOf(100L, 10L, 15L, false, false), // cache is not actual for foreground
                listOf(100L, 10L, 200L, false, false), // cache is not actual for background
            ).forEach { testCase ->
                val (
                    backgroundCacheLifetimeSec,
                    foregroundCacheLifetimeSec,
                    cacheAgeSec,
                    isBackground,
                    expectedResult
                ) = testCase

                // given
                every { mockBackgroundCacheLifetime.seconds } returns backgroundCacheLifetimeSec as Long
                every { mockForegroundCacheLifetime.seconds } returns foregroundCacheLifetimeSec as Long
                every { mockAppLifecycleObserver.isInBackground() } returns isBackground as Boolean

                val cachedObjectTimeMs = Calendar.getInstance().timeInMillis - cacheAgeSec as Long * MS_IN_SEC
                val cachedObject = CachedObject(Date(cachedObjectTimeMs), testCachingValue)

                // when
                val isActual = cacher.isActual(cachedObject)

                // then
                assertThat(isActual == expectedResult as Boolean).isTrue
            }
        }
    }

    @Nested
    inner class GetTest {

        @Test
        fun `get existing value`() {
            // given
            val cachedObject = CachedObject(mockk(), testCachingValue)
            cacher.cachedObjects[testCachingKey] = cachedObject

            // when
            val result = cacher.get(testCachingKey)

            // then
            assertThat(result).isSameAs(testCachingValue)
            verify { mockLocalStorage wasNot called }
        }

        @Test
        fun `get non existing value`() {
            // given
            every { mockLocalStorage.getString(testCachingKey) } returns null

            // when
            val result = cacher.get(testCachingKey)

            // then
            assertThat(result).isNull()
        }
    }

    @Nested
    inner class GetActualTest {

        @Test
        fun `get existing actual value`() {
            // given
            val cacheLifetimeSec = 10000L
            val cacheAgeSec = cacheLifetimeSec - 100L
            every { mockBackgroundCacheLifetime.seconds } returns cacheLifetimeSec
            every { mockAppLifecycleObserver.isInBackground() } returns true

            val cachedObjectTimeMs = Calendar.getInstance().timeInMillis - cacheAgeSec * MS_IN_SEC
            val cachedObject = CachedObject(Date(cachedObjectTimeMs), testCachingValue)
            cacher.cachedObjects[testCachingKey] = cachedObject

            // when
            val result = cacher.getActual(testCachingKey)

            // then
            assertThat(result).isSameAs(testCachingValue)
            verify { mockLocalStorage wasNot called }
        }

        @Test
        fun `get existing non actual value`() {
            // given
            val cacheLifetimeSec = 10000L
            val cacheAgeSec = cacheLifetimeSec + 100L
            every { mockBackgroundCacheLifetime.seconds } returns cacheLifetimeSec
            every { mockAppLifecycleObserver.isInBackground() } returns true

            val cachedObjectTimeMs = Calendar.getInstance().timeInMillis - cacheAgeSec * MS_IN_SEC
            val cachedObject = CachedObject(Date(cachedObjectTimeMs), testCachingValue)
            cacher.cachedObjects[testCachingKey] = cachedObject

            // when
            val result = cacher.getActual(testCachingKey)

            // then
            assertThat(result).isNull()
            verify { mockLocalStorage wasNot called }
        }

        @Test
        fun `get non existing value`() {
            // given
            every { mockLocalStorage.getString(testCachingKey) } returns null

            // when
            val result = cacher.getActual(testCachingKey)

            // then
            assertThat(result).isNull()
        }
    }

    @Nested
    inner class ResetTest {

        @BeforeEach
        fun setUp() {
            every { mockLocalStorage.remove(testCachingKey) } just runs
        }

        @Test
        fun `reset existing value`() {
            // given
            cacher.cachedObjects[testCachingKey] = mockk()

            // when
            cacher.reset(testCachingKey)

            // then
            assertThat(cacher.cachedObjects).doesNotContainKey(testCachingKey)
            verify(exactly = 1) { mockLocalStorage.remove(testCachingKey) }
        }

        @Test
        fun `reset non-existing value`() {
            // given

            // when
            cacher.reset(testCachingKey)

            // then
            assertThat(cacher.cachedObjects).doesNotContainKey(testCachingKey)
            verify(exactly = 1) { mockLocalStorage.remove(testCachingKey) }
        }
    }
}
