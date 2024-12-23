package io.qonversion.nocodes.internal.screen

import io.qonversion.nocodes.R
import io.qonversion.nocodes.dto.QScreenPresentationStyle

internal fun getScreenTransactionAnimations(screenPresentationStyle: QScreenPresentationStyle?) =
    when (screenPresentationStyle) {
        QScreenPresentationStyle.FullScreen -> Pair(
            R.anim.nc_slide_in_from_bottom,
            R.anim.nc_slide_out_to_bottom
        )
        QScreenPresentationStyle.NoAnimation -> Pair(0, 0)
        else -> null
    }