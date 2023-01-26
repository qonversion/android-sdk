package com.qonversion.android.sdk.automations.dto

enum class QScreenPresentationStyle {
    Push, /** default screen transaction animation will be used */
    FullScreen, /** screen will move from bottom to top */
    NoAnimation, /** screen will appear/disappear without any animation */
}
