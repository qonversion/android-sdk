package com.qonversion.android.sdk.internal.dto

import com.qonversion.android.sdk.dto.QRemoteConfigurationAssignmentType
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

internal class QRemoteConfigurationSourceAssignmentTypeAdapterTest {
    private val adapter = Moshi.Builder()
        .add(QRemoteConfigurationSourceAssignmentTypeAdapter())
        .build()
        .adapter(QRemoteConfigurationAssignmentType::class.java)

    @Test
    fun `existing assignment ordinals stay stable and frozen is appended`() {
        assertEquals(
            listOf("Auto", "Manual", "Unknown", "Frozen"),
            QRemoteConfigurationAssignmentType.values().map { it.name },
        )
        assertEquals(listOf(0, 1, 2, 3), QRemoteConfigurationAssignmentType.values().map { it.ordinal })
        assertEquals("Frozen", QRemoteConfigurationAssignmentType.fromType("frozen").name)
    }

    @Test
    fun `frozen assignment has a public enum value and round trips through json`() {
        assertEquals(QRemoteConfigurationAssignmentType.Frozen, adapter.fromJson("\"frozen\""))
        assertEquals("\"frozen\"", adapter.toJson(QRemoteConfigurationAssignmentType.Frozen))
        assertEquals(
            QRemoteConfigurationAssignmentType.Frozen,
            QRemoteConfigurationAssignmentType.fromType("frozen"),
        )
    }

    @Test
    fun `unknown future assignment remains forward compatible`() {
        assertEquals(QRemoteConfigurationAssignmentType.Unknown, adapter.fromJson("\"future-type\""))
        assertEquals(QRemoteConfigurationAssignmentType.Unknown, QRemoteConfigurationAssignmentType.fromType("future-type"))
    }
}
