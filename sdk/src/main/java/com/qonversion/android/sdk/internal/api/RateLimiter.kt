package com.qonversion.android.sdk.internal.api

import com.qonversion.android.sdk.internal.toInt

private const val MS_IN_SEC = 1000

internal class RateLimiter(private val maxRequestsPerSecond: Int) {
    private val requests = mutableMapOf<RequestType, MutableList<Request>>()

    @Synchronized
    fun saveRequest(requestType: RequestType, hash: Int) {
        val ts = System.currentTimeMillis()

        if (!requests.containsKey(requestType)) {
            requests[requestType] = mutableListOf()
        }

        val request = Request(hash, ts)
        requests[requestType]?.add(request)
    }

    @Synchronized
    fun isRateLimitExceeded(requestType: RequestType, hash: Int): Boolean {
        removeOutdatedRequests(requestType)

        val requestsPerType = requests[requestType] ?: emptyList()

        var matchCount = 0
        for (request in requestsPerType) {
            if (matchCount >= maxRequestsPerSecond) {
                break
            }

            matchCount += (request.hash == hash).toInt()
        }

        return matchCount >= maxRequestsPerSecond
    }

    @Synchronized
    private fun removeOutdatedRequests(requestType: RequestType) {
        val ts = System.currentTimeMillis()
        val requestsPerType = requests[requestType] ?: emptyList()
        requests[requestType] = requestsPerType
            .filter { ts - it.timestamp < MS_IN_SEC }
            .toMutableList()
    }

    private class Request(
        val hash: Int,
        val timestamp: Long
    )
}
