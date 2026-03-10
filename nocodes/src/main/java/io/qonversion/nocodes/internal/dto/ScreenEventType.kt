package io.qonversion.nocodes.internal.dto

/**
 * SDK-originated screen lifecycle events only.
 * JS-originated events (e.g. CTA taps, page views) are passed through
 * via [ScreenEvent] without validation against this enum.
 */
internal enum class ScreenEventType(val value: String) {
    ScreenShown("screen_shown"),
    ScreenClosed("screen_closed");
}
