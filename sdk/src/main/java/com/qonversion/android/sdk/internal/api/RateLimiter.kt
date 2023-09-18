package com.qonversion.android.sdk.internal.api

import com.qonversion.android.sdk.internal.toInt

private const val MS_IN_SEC = 1000

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

    private fun removeOutdatedRequests(requestType: RequestType) {
        val requestsPerType = requests[requestType] ?: emptyList()

        var i = 0
        val ts = System.currentTimeMillis()

        while (i < requestsPerType.size && ts - requestsPerType[i].timestamp >= MS_IN_SEC) {
            ++i
        }

        if (i > 0) {
            if (i == requestsPerType.size) {
                requests[requestType] = mutableListOf()
            } else {
                val filteredRequests = requestsPerType.subList(i, requestsPerType.size)
                requests[requestType] = filteredRequests.toMutableList()
            }
        }
    }

    private class Request(
        val hash: Int,
        val timestamp: Long
    )
}
