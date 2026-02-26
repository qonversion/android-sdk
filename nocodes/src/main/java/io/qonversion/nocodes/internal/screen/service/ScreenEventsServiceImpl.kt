package io.qonversion.nocodes.internal.screen.service

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QUser
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.listeners.QonversionUserCallback
import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.dto.ScreenEvent
import io.qonversion.nocodes.internal.logger.Logger
import io.qonversion.nocodes.internal.networkLayer.apiInteractor.ApiInteractor
import io.qonversion.nocodes.internal.networkLayer.requestConfigurator.RequestConfigurator
import io.qonversion.nocodes.internal.networkLayer.dto.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class ScreenEventsServiceImpl(
    private val requestConfigurator: RequestConfigurator,
    private val apiInteractor: ApiInteractor,
    private val logger: Logger,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ScreenEventsService {

    private val lock = Any()
    private val buffer = mutableListOf<ScreenEvent>()
    private var isFlushing = false
    @Volatile
    private var cachedUserId: String? = null

    override fun track(event: ScreenEvent) {
        var shouldFlush = false
        synchronized(lock) {
            buffer.add(event)
            shouldFlush = buffer.size >= BATCH_SIZE
        }
        logger.verbose("ScreenEventsService -> tracked event: ${event.type.value}")
        if (shouldFlush) {
            flush()
        }
    }

    override fun flush() {
        val eventsToSend: List<ScreenEvent>
        synchronized(lock) {
            if (isFlushing || buffer.isEmpty()) return
            isFlushing = true
            eventsToSend = buffer.toList()
            buffer.clear()
        }

        scope.launch {
            sendEvents(eventsToSend)
        }
    }

    override suspend fun flushAndWait() {
        val eventsToSend: List<ScreenEvent>
        synchronized(lock) {
            if (isFlushing || buffer.isEmpty()) return
            isFlushing = true
            eventsToSend = buffer.toList()
            buffer.clear()
        }

        withContext(scope.coroutineContext) {
            sendEvents(eventsToSend)
        }
    }

    private suspend fun sendEvents(eventsToSend: List<ScreenEvent>) {
        try {
            val uid = getUserId()
            val eventMaps = eventsToSend.map { it.toMap() }
            val body = mapOf<String, Any?>("events" to eventMaps)
            val request = requestConfigurator.configureScreenEventsRequest(uid, body)
            val response = apiInteractor.execute(request)

            if (response is Response.Error) {
                logger.error("ScreenEventsService -> failed to send events: ${response.message}")
                reBuffer(eventsToSend)
            } else {
                logger.verbose("ScreenEventsService -> sent ${eventsToSend.size} events")
            }
        } catch (e: Exception) {
            logger.error("ScreenEventsService -> failed to send events: $e")
            reBuffer(eventsToSend)
        } finally {
            val shouldFlushAgain: Boolean
            synchronized(lock) {
                isFlushing = false
                shouldFlushAgain = buffer.isNotEmpty()
            }
            if (shouldFlushAgain) {
                flush()
            }
        }
    }

    private suspend fun getUserId(): String {
        cachedUserId?.let { return it }

        return suspendCancellableCoroutine { continuation ->
            Qonversion.shared.userInfo(object : QonversionUserCallback {
                override fun onSuccess(user: QUser) {
                    cachedUserId = user.qonversionId
                    continuation.resume(user.qonversionId)
                }

                override fun onError(error: QonversionError) {
                    continuation.resumeWithException(
                        NoCodesException(ErrorCode.BackendError, "Failed to get user info: ${error.description}")
                    )
                }
            })
        }
    }

    private fun reBuffer(events: List<ScreenEvent>) {
        synchronized(lock) {
            buffer.addAll(0, events)
            if (buffer.size > MAX_BUFFER_SIZE) {
                // Keep oldest (failed) events, trim newest
                while (buffer.size > MAX_BUFFER_SIZE) {
                    buffer.removeAt(buffer.size - 1)
                }
            }
        }
    }

    companion object {
        private const val BATCH_SIZE = 10
        private const val MAX_BUFFER_SIZE = 100
    }
}
