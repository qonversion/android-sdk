package com.qonversion.android.sdk.automations.internal

import com.qonversion.android.sdk.R
import com.qonversion.android.sdk.automations.dto.QScreenPresentationStyle

internal fun getScreenTransactionAnimations(screenPresentationStyle: QScreenPresentationStyle?) =
    when (screenPresentationStyle) {
        QScreenPresentationStyle.FULL_SCREEN -> Pair(
            R.anim.slide_in_from_bottom,
            R.anim.slide_out_to_bottom
        )
        QScreenPresentationStyle.NO_ANIMATION -> Pair(0, 0)
        else -> null
    }
