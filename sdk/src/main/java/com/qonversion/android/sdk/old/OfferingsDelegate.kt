package com.qonversion.android.sdk.old

import com.qonversion.android.sdk.old.dto.offerings.QOffering

internal interface OfferingsDelegate {
    fun offeringByIDWasCalled(offering: QOffering?)
}
