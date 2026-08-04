package com.qonversion.android.sdk.dto

import org.junit.Assert.assertEquals
import org.junit.Test

class QRemoteConfigurationAssignmentTypeTest {
    @Test
    fun `frozen wire value preserves assignment provenance`() {
        assertEquals(
            QRemoteConfigurationAssignmentType.Frozen,
            QRemoteConfigurationAssignmentType.fromType("frozen")
        )
    }

    @Test
    fun `existing enum ordinals and unknown fallback remain compatible`() {
        assertEquals(0, QRemoteConfigurationAssignmentType.Auto.ordinal)
        assertEquals(1, QRemoteConfigurationAssignmentType.Manual.ordinal)
        assertEquals(2, QRemoteConfigurationAssignmentType.Unknown.ordinal)
        assertEquals(
            QRemoteConfigurationAssignmentType.Unknown,
            QRemoteConfigurationAssignmentType.fromType("future")
        )
    }
}
