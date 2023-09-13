package com.qonversion.android.sdk.internal.api

import android.util.Log
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

internal class RateLimiter(maxRequestsPerSecond: Int) {
    private val tokens: BlockingQueue<Any> = ArrayBlockingQueue(maxRequestsPerSecond)
    private val refillIntervalMs: Long = 1000
    private val tokensPerRefill = maxRequestsPerSecond
    private var lastRefillTime = System.currentTimeMillis()
    private var refillScheduleTimer: Timer? = null

    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val elapsedTime = now - lastRefillTime
        val tokensToAdd = (elapsedTime / refillIntervalMs).toInt() * tokensPerRefill
        if (tokensToAdd > 0) {
            repeat(tokensToAdd) {
                tokens.offer(Any())
            }
            lastRefillTime = now
        }

        scheduleTokensRefilling()
    }

    private fun scheduleTokensRefilling() {
        try {
            refillScheduleTimer?.cancel()
            refillScheduleTimer = Timer("Tokens refilling timer", false).apply {
                schedule(object : TimerTask() {
                    override fun run() {
                        refillTokens()
                    }
                }, refillIntervalMs)
            }
        } catch (_: RuntimeException) {
            Log.e("Qonversion", "Failed to manage rate limits refreshing")
        }
    }
}