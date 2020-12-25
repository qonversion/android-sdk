package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.QonversionRequest
import com.qonversion.android.sdk.logger.ConsoleLogger
import java.util.*

class RequestsQueue {
    private val logger = ConsoleLogger()

    private val queue: Queue<QonversionRequest> = LinkedList()

    @Synchronized fun add(request: QonversionRequest) {
        queue.add(request)
        logger.debug("RequestsQueue: add: [${request.javaClass.simpleName}] size: [${queue.size}]")
    }

    @Synchronized fun isEmpty() : Boolean {
        return queue.isEmpty()
    }

    @Synchronized fun poll() : QonversionRequest? {
        val request = queue.poll()
        logger.debug("RequestsQueue: poll: [${request?.javaClass?.simpleName}] size: [${queue.size}]")
        return request
    }

    @Synchronized fun size() : Int = queue.size
}