package io.qonversion.nocodes.internal.dto

internal enum class ScreenEventType(val value: String) {
    ScreenShown("screen_shown"),
    ScreenClosed("screen_closed"),
    CtaTap("screen_cta_tap"),
    PageView("screen_page_view");
}
