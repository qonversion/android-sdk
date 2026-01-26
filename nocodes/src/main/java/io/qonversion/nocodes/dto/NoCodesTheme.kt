package io.qonversion.nocodes.dto

/**
 * Enum representing the theme mode for No-Code screens.
 * Use this to control how screens adapt to light/dark themes.
 */
enum class NoCodesTheme(val value: String) {
    /**
     * Automatically follow the device's system appearance (default).
     * The screen will use light theme in light mode and dark theme in dark mode.
     */
    Auto("auto"),

    /**
     * Force light theme regardless of device settings.
     */
    Light("light"),

    /**
     * Force dark theme regardless of device settings.
     */
    Dark("dark")
}
