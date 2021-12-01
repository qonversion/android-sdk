package com.qonversion.android.sdk.dto

import java.util.Date

data class Subscription(
    val duration: ProductDuration,
    val startedDate: Date,
    val currentPeriodStartedDate: Date,
    val currentPeriodEndDate: Date,
    val currentPeriodType: PeriodType,
    val renewState: RenewState
)
