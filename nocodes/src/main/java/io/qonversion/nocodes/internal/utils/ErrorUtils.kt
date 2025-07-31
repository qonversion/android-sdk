package io.qonversion.nocodes.internal.utils

import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

object ErrorUtils {

    // Network error keywords
    private val NETWORK_KEYWORDS = listOf(
        "network",
        "connection",
        "timeout",
        "dns",
        "unreachable"
    )

    // HTTP status codes that indicate server errors
    private val SERVER_STATUS_CODES = listOf(
        "403", "451", "429", "500", "502", "503", "504"
    )

    // Server error keywords
    private val SERVER_KEYWORDS = listOf(
        "blocked", "forbidden", "unavailable", "restricted",
        "geoblocked", "censored", "sanctioned", "rate limit",
        "server error", "internal error", "bad gateway",
        "service unavailable", "gateway timeout"
    )

    /**
     * Checks if the error is a network-related error that should trigger fallback
     */
    fun isNetworkError(error: Exception): Boolean {
        return when (error) {
            is UnknownHostException,
            is SocketTimeoutException,
            is ConnectException,
            is SocketException,
            is TimeoutException -> true
            else -> {
                val message = error.message?.lowercase() ?: ""
                NETWORK_KEYWORDS.any { message.contains(it) }
            }
        }
    }

    /**
     * Checks if the error is a server-related error that should trigger fallback
     */
    fun isServerError(error: Exception): Boolean {
        val message = error.message?.lowercase() ?: ""

        // Check for HTTP status codes
        if (SERVER_STATUS_CODES.any { message.contains(it) }) {
            return true
        }

        // Check for server error keywords
        if (SERVER_KEYWORDS.any { message.contains(it) }) {
            return true
        }

        // Check for network errors that indicate server issues
        return when (error) {
            is UnknownHostException,
            is ConnectException,
            is SocketException -> true
            else -> false
        }
    }

    /**
     * Checks if the error should trigger fallback (either network or server error)
     */
    fun shouldTriggerFallback(error: Exception): Boolean {
        return isNetworkError(error) || isServerError(error)
    }
}
