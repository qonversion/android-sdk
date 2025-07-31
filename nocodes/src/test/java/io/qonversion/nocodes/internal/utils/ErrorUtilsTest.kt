package io.qonversion.nocodes.internal.utils

import org.junit.Test
import org.junit.Assert.*
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

class ErrorUtilsTest {

    @Test
    fun `isNetworkError should return true for network exceptions`() {
        assertTrue(ErrorUtils.isNetworkError(UnknownHostException("DNS resolution failed")))
        assertTrue(ErrorUtils.isNetworkError(SocketTimeoutException("Connection timeout")))
        assertTrue(ErrorUtils.isNetworkError(ConnectException("Connection refused")))
        assertTrue(ErrorUtils.isNetworkError(SocketException("Socket closed")))
        assertTrue(ErrorUtils.isNetworkError(TimeoutException("Operation timed out")))
    }

    @Test
    fun `isNetworkError should return true for network-related error messages`() {
        assertTrue(ErrorUtils.isNetworkError(Exception("Network unreachable")))
        assertTrue(ErrorUtils.isNetworkError(Exception("Connection failed")))
        assertTrue(ErrorUtils.isNetworkError(Exception("DNS lookup failed")))
        assertTrue(ErrorUtils.isNetworkError(Exception("Connection timeout")))
    }

    @Test
    fun `isNetworkError should return false for non-network errors`() {
        assertFalse(ErrorUtils.isNetworkError(Exception("Invalid JSON")))
        assertFalse(ErrorUtils.isNetworkError(Exception("Authentication failed")))
        assertFalse(ErrorUtils.isNetworkError(Exception("File not found")))
    }

    @Test
    fun `isServerError should return true for server error status codes`() {
        assertTrue(ErrorUtils.isServerError(Exception("HTTP 500 Internal Server Error")))
        assertTrue(ErrorUtils.isServerError(Exception("HTTP 502 Bad Gateway")))
        assertTrue(ErrorUtils.isServerError(Exception("HTTP 503 Service Unavailable")))
        assertTrue(ErrorUtils.isServerError(Exception("HTTP 504 Gateway Timeout")))
        assertTrue(ErrorUtils.isServerError(Exception("HTTP 429 Too Many Requests")))
        assertTrue(ErrorUtils.isServerError(Exception("HTTP 403 Forbidden")))
        assertTrue(ErrorUtils.isServerError(Exception("HTTP 451 Unavailable For Legal Reasons")))
    }

    @Test
    fun `isServerError should return true for server-related error keywords`() {
        assertTrue(ErrorUtils.isServerError(Exception("Service blocked by provider")))
        assertTrue(ErrorUtils.isServerError(Exception("Access forbidden in this region")))
        assertTrue(ErrorUtils.isServerError(Exception("Service unavailable due to maintenance")))
        assertTrue(ErrorUtils.isServerError(Exception("Rate limit exceeded")))
        assertTrue(ErrorUtils.isServerError(Exception("Content censored by government")))
        assertTrue(ErrorUtils.isServerError(Exception("Service sanctioned")))
        assertTrue(ErrorUtils.isServerError(Exception("Internal server error occurred")))
        assertTrue(ErrorUtils.isServerError(Exception("Bad gateway error")))
        assertTrue(ErrorUtils.isServerError(Exception("Gateway timeout")))
    }

    @Test
    fun `isServerError should return true for network errors that indicate server issues`() {
        assertTrue(ErrorUtils.isServerError(UnknownHostException("DNS resolution failed")))
        assertTrue(ErrorUtils.isServerError(ConnectException("Connection refused")))
        assertTrue(ErrorUtils.isServerError(SocketException("Socket closed")))
    }

    @Test
    fun `isServerError should return false for client errors`() {
        assertFalse(ErrorUtils.isServerError(Exception("HTTP 404 Not Found")))
        assertFalse(ErrorUtils.isServerError(Exception("HTTP 400 Bad Request")))
        assertFalse(ErrorUtils.isServerError(Exception("HTTP 401 Unauthorized")))
    }

    @Test
    fun `shouldTriggerFallback should return true for network or server errors`() {
        assertTrue(ErrorUtils.shouldTriggerFallback(UnknownHostException("DNS failed")))
        assertTrue(ErrorUtils.shouldTriggerFallback(Exception("HTTP 500 Server Error")))
        assertTrue(ErrorUtils.shouldTriggerFallback(Exception("Service blocked")))
        assertTrue(ErrorUtils.shouldTriggerFallback(Exception("Network timeout")))
    }

    @Test
    fun `shouldTriggerFallback should return false for other errors`() {
        assertFalse(ErrorUtils.shouldTriggerFallback(Exception("Invalid JSON format")))
        assertFalse(ErrorUtils.shouldTriggerFallback(Exception("Authentication failed")))
        assertFalse(ErrorUtils.shouldTriggerFallback(Exception("File not found")))
    }
} 