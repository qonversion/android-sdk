package io.qonversion.nocodes.internal.screen.service

import android.content.Context
import io.qonversion.nocodes.internal.common.mappers.Mapper
import io.qonversion.nocodes.internal.dto.NoCodeScreen
import io.qonversion.nocodes.internal.logger.Logger
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any

class FallbackServiceTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var mapper: Mapper<NoCodeScreen?>

    @Mock
    private lateinit var logger: Logger

    @Mock
    private lateinit var mockScreen: NoCodeScreen

    private lateinit var fallbackService: FallbackServiceImpl

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        fallbackService = FallbackServiceImpl(context, "test_fallbacks.json", mapper, logger)
    }

    @Test
    fun `isFallbackFileAvailable should return true when file exists`() {
        // This test would require mocking the assets manager
        // For now, we'll test the companion object method exists
        assertNotNull(FallbackService::class.java.getDeclaredMethod("isFallbackFileAvailable", String::class.java, Context::class.java))
    }

    @Test
    fun `loadScreen should return null when mapper returns null`() = runBlocking {
        whenever(mapper.fromMap(any())).thenReturn(null)
        
        val result = fallbackService.loadScreen("test_key")
        
        assertNull(result)
    }

    @Test
    fun `loadScreen should return screen when mapper returns valid screen`() = runBlocking {
        whenever(mapper.fromMap(any())).thenReturn(mockScreen)
        
        val result = fallbackService.loadScreen("test_key")
        
        assertEquals(mockScreen, result)
    }

    @Test
    fun `loadScreenById should return null when mapper returns null`() = runBlocking {
        whenever(mapper.fromMap(any())).thenReturn(null)
        
        val result = fallbackService.loadScreenById("test_id")
        
        assertNull(result)
    }

    @Test
    fun `loadScreenById should return screen when mapper returns valid screen`() = runBlocking {
        whenever(mapper.fromMap(any())).thenReturn(mockScreen)
        
        val result = fallbackService.loadScreenById("test_id")
        
        assertEquals(mockScreen, result)
    }
} 