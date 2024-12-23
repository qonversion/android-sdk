package io.qonversion.nocodes.internal.common.mappers

import io.qonversion.nocodes.internal.dto.NoCodeScreen

internal class ScreenMapper : Mapper<NoCodeScreen?> {

    override fun fromMap(data: Map<*, *>): NoCodeScreen? {
        val id = data.getString("id")
        val body = data.getString("body")

        if (id == null || body == null) {
            return null
        }

        return NoCodeScreen(
            id,
            body
        )
    }
}