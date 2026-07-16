package io.qonversion.nocodes.dto

/**
 * A loaded No-Code screen returned from [io.qonversion.nocodes.NoCodes.loadScreen].
 *
 * Exposes the screen identifiers and the typed default variables configured in the builder —
 * the screen content stays internal, as rendering remains the SDK's job via
 * [io.qonversion.nocodes.NoCodes.showScreen].
 *
 * @property id identifier of the screen.
 * @property contextKey the context key of the screen set in the No-Codes builder.
 * @property defaultVariables typed default variables configured in the builder — authored
 * custom variables and product slots. Read them by [QScreenVariable.key] (may be empty).
 */
data class QNoCodeScreen(
    val id: String,
    val contextKey: String,
    val defaultVariables: List<QScreenVariable> = emptyList(),
) {

    /**
     * Returns the default variable configured under the given [key], or `null` when the
     * screen has no variable with that exact (case-sensitive) key.
     *
     * Keys are only unique within a kind — a custom variable and a product slot may share
     * a name — so pass [kind] to disambiguate; without it the first match in payload order
     * (custom variables, then product slots, then the selected product) is returned.
     */
    @JvmOverloads
    fun defaultVariable(key: String, kind: QScreenVariable.Kind? = null): QScreenVariable? =
        defaultVariables.firstOrNull { it.key == key && (kind == null || it.kind == kind) }
}
