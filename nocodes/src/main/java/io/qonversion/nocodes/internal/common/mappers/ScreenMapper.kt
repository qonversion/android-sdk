package io.qonversion.nocodes.internal.common.mappers

import io.qonversion.nocodes.dto.QScreenVariable
import io.qonversion.nocodes.internal.dto.NoCodeScreen

internal class ScreenMapper : Mapper<NoCodeScreen?> {

    override fun fromMap(data: Map<*, *>): NoCodeScreen? {
        val id = data.getString("id")
        val body = data.getString("body")
        val contextKey = data.getString("context_key")

        if (id == null || body == null || contextKey == null) {
            return null
        }

        // Missing/absent `products` (older payloads, bundled fallbacks) → empty list.
        val products = data.getList("products")?.filterIsInstance<String>() ?: emptyList()

        // Missing/absent `variables` → empty list. Each entry is {key, type, value}; the value
        // keeps its native type. Entries without a key are skipped; a missing type falls back to
        // "string" (mirrors the backend extractor).
        val variables = data.getList("variables")
            ?.filterIsInstance<Map<*, *>>()
            ?.mapNotNull { item ->
                val key = item.getString("key") ?: return@mapNotNull null
                val type = item.getString("type") ?: "string"
                QScreenVariable(key, type, item["value"])
            } ?: emptyList()

        return NoCodeScreen(
            id,
            body,
            contextKey,
            products,
            variables,
        )
    }
}
