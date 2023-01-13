package com.qonversion.android.sdk.automations.dto

enum class QScreenPresentationStyle {
    PUSH, /** default screen transaction animation will be used */
    FULL_SCREEN, /** screen will move from bottom to top */
    NO_ANIMATION, /** screen will appear/disappear without any animation */
}
