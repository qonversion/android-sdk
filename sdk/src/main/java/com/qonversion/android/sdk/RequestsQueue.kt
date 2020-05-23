package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.QonversionRequest
import com.qonversion.android.sdk.logger.Logger
import java.util.*

class RequestsQueue(private val logger: Logger) {

    private val queue: Queue<QonversionRequest> = LinkedList()

    @Synchronized fun add(request: QonversionRequest) {
        queue.add(request)
        logger.log("RequestsQueue: add: [${request.javaClass.simpleName}] size: [${queue.size}]")
    }

    @Synchronized fun isEmpty() : Boolean {
        return queue.isEmpty()
    }

    @Synchronized fun poll() : QonversionRequest? {
        val request = queue.poll()
        logger.log("RequestsQueue: poll: [${request?.javaClass?.simpleName}] size: [${queue.size}]")
        return request
    }

    @Synchronized fun size() : Int = queue.size
}