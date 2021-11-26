package com.qonversion.android.sdk.public

import java.util.Date

data class Subscription(
    val duration: ProductDuration,
    val startDate: Date,
    val currentPeriodStartDate: Date,
    val currentPeriodEndDate: Date,
    val currentPeriodType: PeriodType,
    val renewState: RenewState
    )
