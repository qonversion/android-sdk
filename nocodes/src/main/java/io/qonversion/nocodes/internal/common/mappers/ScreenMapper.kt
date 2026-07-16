package io.qonversion.nocodes.internal.common.mappers

import io.qonversion.nocodes.dto.QScreenVariable
import io.qonversion.nocodes.dto.QScreenVariableValue
import io.qonversion.nocodes.internal.dto.NoCodeScreen

internal class ScreenMapper : Mapper<NoCodeScreen?> {

    override fun fromMap(data: Map<*, *>): NoCodeScreen? {
        val id = data.getString("id")
        val body = data.getString("body")
        val contextKey = data.getString("context_key")

        if (id == null || body == null || contextKey == null) {
            return null
        }

        // Missing/absent `variables` (older payloads, bundled fallbacks) → empty list.
        // Each entry is {kind, key, type, value}; the value keeps its native type. Entries
        // without a key are skipped; a missing type falls back to "string" (mirrors the
        // backend extractor).
        val variables = data.getList("variables")
            ?.filterIsInstance<Map<*, *>>()
            ?.mapNotNull { item ->
                val key = item.getString("key") ?: return@mapNotNull null
                val type = item.getString("type") ?: "string"
                QScreenVariable(mapVariableKind(item.getString("kind")), key, type, mapVariableValue(item["value"]))
            } ?: emptyList()

        return NoCodeScreen(
            id,
            body,
            contextKey,
            variables,
        )
    }

    // A missing kind means a payload predating the field, which carries only authored
    // custom variables; an unknown kind (added on the backend after this SDK version)
    // must not fail the screen load.
    private fun mapVariableKind(rawKind: String?): QScreenVariable.Kind = when (rawKind) {
        null, KIND_CUSTOM -> QScreenVariable.Kind.Custom
        KIND_PRODUCT -> QScreenVariable.Kind.Product
        KIND_SELECTED_PRODUCT -> QScreenVariable.Kind.SelectedProduct
        else -> QScreenVariable.Kind.Unknown
    }

    private fun mapVariableValue(rawValue: Any?): QScreenVariableValue = when (rawValue) {
        is Boolean -> QScreenVariableValue.Bool(rawValue)
        is Number -> QScreenVariableValue.Num(rawValue.toDouble())
        is String -> QScreenVariableValue.Str(rawValue)
        else -> QScreenVariableValue.None
    }

    companion object {
        private const val KIND_CUSTOM = "custom"
        private const val KIND_PRODUCT = "product"
        private const val KIND_SELECTED_PRODUCT = "selected_product"
    }
}
