package io.qonversion.nocodes.internal.utils

import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

object ErrorUtils {
    
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
                message.contains("network") || 
                message.contains("connection") || 
                message.contains("timeout") ||
                message.contains("dns") ||
                message.contains("unreachable")
            }
        }
    }
    
    /**
     * Checks if the error is a server-related error that should trigger fallback
     */
    fun isServerError(error: Exception): Boolean {
        // Check for HTTP status codes in error message
        val message = error.message?.lowercase() ?: ""
        val statusCodePatterns = listOf("403", "451", "429", "500", "502", "503", "504")
        
        if (statusCodePatterns.any { message.contains(it) }) {
            return true
        }
        
        // Check for specific error keywords
        val errorKeywords = listOf(
            "blocked", "forbidden", "unavailable", "restricted", 
            "geoblocked", "censored", "sanctioned", "rate limit",
            "server error", "internal error", "bad gateway",
            "service unavailable", "gateway timeout"
        )
        
        if (errorKeywords.any { message.contains(it) }) {
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