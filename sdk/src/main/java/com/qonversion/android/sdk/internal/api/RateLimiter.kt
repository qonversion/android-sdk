package com.qonversion.android.sdk.internal.api

import com.qonversion.android.sdk.internal.toInt

private const val MS_IN_SEC = 10000

internal class RateLimiter(private val maxRequestsPerSecond: Int) {
    private val requests = mutableMapOf<RequestType, MutableList<Request>>()

    fun saveRequest(requestType: RequestType, hash: Int) {
        val ts = System.currentTimeMillis()

        if (!requests.containsKey(requestType)) {
            requests[requestType] = mutableListOf()
        }

        val request = Request(hash, ts)
        requests[requestType]?.add(request)
    }

    fun isRateLimitExceeded(requestType: RequestType, hash: Int): Boolean {
        val requestsPerType = requests[requestType] ?: emptyList()

        var matchCount = 0
        val ts = System.currentTimeMillis()
        for (request in requestsPerType.reversed()) {
            if (ts - request.timestamp >= MS_IN_SEC || matchCount >= maxRequestsPerSecond) {
                break
            }

            matchCount += (request.hash == hash).toInt()
        }

        return matchCount >= maxRequestsPerSecond
    }

    private class Request(
        val hash: Int,
        val timestamp: Long
    )
}
