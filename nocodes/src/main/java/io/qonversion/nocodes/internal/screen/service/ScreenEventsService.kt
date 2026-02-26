package io.qonversion.nocodes.internal.screen.service

import io.qonversion.nocodes.internal.dto.ScreenEvent

internal interface ScreenEventsService {
    fun track(event: ScreenEvent)
    fun flush()
}
