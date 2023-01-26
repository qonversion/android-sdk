package com.qonversion.android.sdk.automations.internal

import com.qonversion.android.sdk.R
import com.qonversion.android.sdk.automations.dto.QScreenPresentationStyle

internal fun getScreenTransactionAnimations(screenPresentationStyle: QScreenPresentationStyle?) =
    when (screenPresentationStyle) {
        QScreenPresentationStyle.FullScreen -> Pair(
            R.anim.q_slide_in_from_bottom,
            R.anim.q_slide_out_to_bottom
        )
        QScreenPresentationStyle.NoAnimation -> Pair(0, 0)
        else -> null
    }
