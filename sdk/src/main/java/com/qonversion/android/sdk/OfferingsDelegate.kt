package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.offerings.QOffering

internal interface OfferingsDelegate {
    fun offeringByIDWasCalled(offering: QOffering?)
}