package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.provider.CacheLifetimeConfigProvider
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.cache.mapper.CacheMapper
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.utils.MS_IN_SEC
import io.mockk.called
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.Calendar
import java.util.Date

private typealias CacheObjectType = String

internal class CacherTest {

    private lateinit var cacher: CacherImpl<CacheObjectType>
    private val mockCacheMapper = mockk<CacheMapper<CacheObjectType>>()
    private val mockLocalStorage = mockk<LocalStorage>()
    private val mockAppLifecycleObserver = mockk<AppLifecycleObserver>()
    private val mockBackgroundCacheLifetime = mockk<InternalCacheLifetime>()
    private val mockForegroundCacheLifetime = mockk<InternalCacheLifetime>()
    private val mockLogger = mockk<Logger>()

    private val testCachingKey: String = "test caching key"
    private val testCachingValue: CacheObjectType = "test caching value"

    @BeforeEach
    fun setUp() {
        val mockCacheLifetimeConfigProvider = mockk<CacheLifetimeConfigProvider>()
        every {
            mockCacheLifetimeConfigProvider.cacheLifetimeConfig
        } returns CacheLifetimeConfig(
            mockBackgroundCacheLifetime,
            mockForegroundCacheLifetime
        )

        cacher = CacherImpl(
            mockCacheMapper,
            mockLocalStorage,
            mockAppLifecycleObserver,
            mockCacheLifetimeConfigProvider,
            mockLogger
        )
    }

    @Nested
    inner class StoreTest {

        private val slotCachedObject = slot<CachedObject<CacheObjectType>>()
        private val mappedObject = "mapped object"

        @BeforeEach
        fun setUp() {
            every { mockCacheMapper.toSerializedString(capture(slotCachedObject)) } returns mappedObject
            every { mockLocalStorage.putString(any(), mappedObject) } just runs
        }

        @Test
        fun `store successful`() {
            // given
            val mockDate = mockk<Date>()
            mockkStatic(Calendar::class)
            every { Calendar.getInstance().time } returns mockDate

            // when
            cacher.store(testCachingKey, testCachingValue)

            // then
            verify(exactly = 1) {
                mockCacheMapper.toSerializedString(any())
                mockLocalStorage.putString(testCachingKey, mappedObject)
            }
            assertThat(slotCachedObject.captured.value).isSameAs(testCachingValue)
            assertThat(slotCachedObject.captured.date).isSameAs(mockDate)
            assertThat(cacher.cachedObjects[testCachingKey]).isSameAs(slotCachedObject.captured)

            clearMocks(Calendar.getInstance().time)

            unmockkStatic(Calendar::class)
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

        @Test
        fun `mapper throws exception`() {
            // given
            val exception = QonversionException(ErrorCode.Deserialization)
            every { mockLogger.error(any(), exception) } just runs

            every { mockLocalStorage.getString(testCachingKey) } returns testStoredValue
            every { mockCacheMapper.fromSerializedString(testStoredValue) } throws exception

            // when
            val result = assertDoesNotThrow {
                cacher.load(testCachingKey)
            }

            // then
            assertThat(result).isNull()
            verify(exactly = 1) {
                mockLocalStorage.getString(testCachingKey)
                mockCacheMapper.fromSerializedString(testStoredValue)
            }
        }
    }

    @Nested
    inner class IsActualTest {
        @BeforeEach
        fun setUp() {
            cacher = spyk(cacher)
        }

        @Test
        fun `all tests`() {
            listOf(
                listOf(5L, 100L, CacheState.Default, true), // cache is actual for default state(<)
                listOf(5L, 100L, CacheState.Error, true), // cache is actual for error state(<)
                listOf(100L, 100L, CacheState.Default, true), // cache is actual for default state(=)
                listOf(100L, 100L, CacheState.Error, true), // cache is actual for error state(=)
                listOf(200L, 100L, CacheState.Default, false), // cache is not actual for default state(>)
                listOf(200L, 100L, CacheState.Error, false), // cache is not actual for error state(>)
            ).forEach { testCase ->
                val (
                    cacheAgeSec,
                    maxCacheLifetimeSec,
                    cacheState,
                    expectedResult
                ) = testCase

                // given
                every { cacher.getMaxCacheLifetimeSec(any()) } returns maxCacheLifetimeSec as Long

                val cachedObjectTimeMs = Calendar.getInstance().timeInMillis - cacheAgeSec as Long * MS_IN_SEC
                val cachedObject = CachedObject(Date(cachedObjectTimeMs), testCachingValue)

                // when
                val isActual = cacher.isActual(cachedObject, cacheState as CacheState)

                // then
                assertThat(isActual == expectedResult as Boolean).isTrue
                verify {
                    cacher.getMaxCacheLifetimeSec(cacheState)
                }
            }
        }
    }

    @Nested
    inner class GetMaxCacheLifetimeSecTest {
        @Test
        fun `all tests`() {
            // given
            val backgroundCacheLifetimeSec = 50L
            val foregroundCacheLifetimeSec = 10L

            listOf(
                listOf(CacheState.Default, true, backgroundCacheLifetimeSec), // background and default state
                listOf(CacheState.Error, true, backgroundCacheLifetimeSec), // background and error state
                listOf(CacheState.Default, false, foregroundCacheLifetimeSec), // foreground and default state
                listOf(CacheState.Error, false, backgroundCacheLifetimeSec), // foreground and error state
            ).forEach { testCase ->
                val (
                    cacheState,
                    isBackground,
                    expectedResult
                ) = testCase

                // given
                every { mockBackgroundCacheLifetime.seconds } returns backgroundCacheLifetimeSec
                every { mockForegroundCacheLifetime.seconds } returns foregroundCacheLifetimeSec
                every { mockAppLifecycleObserver.isInBackground() } returns isBackground as Boolean

                // when
                val maxCacheLifetimeSec = cacher.getMaxCacheLifetimeSec(cacheState as CacheState)

                // then
                assertThat(expectedResult).isSameAs(maxCacheLifetimeSec)
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

        @BeforeEach
        fun setUp() {
            cacher = spyk(cacher)
        }

        @Test
        fun `get existing actual value`() {
            // given
            val cachedObject = CachedObject(mockk(), testCachingValue)
            val mockCacheState = mockk<CacheState>()
            every { cacher.isActual(cachedObject, mockCacheState) } returns true
            cacher.cachedObjects[testCachingKey] = cachedObject

            // when
            val result = cacher.getActual(testCachingKey, mockCacheState)

            // then
            assertThat(result).isSameAs(testCachingValue)
            verify { mockLocalStorage wasNot called }
        }

        @Test
        fun `get existing non actual value`() {
            // given
            val cachedObject = CachedObject(mockk(), testCachingValue)
            val mockCacheState = mockk<CacheState>()
            every { cacher.isActual(cachedObject, mockCacheState) } returns false
            cacher.cachedObjects[testCachingKey] = cachedObject

            // when
            val result = cacher.getActual(testCachingKey, mockCacheState)

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
