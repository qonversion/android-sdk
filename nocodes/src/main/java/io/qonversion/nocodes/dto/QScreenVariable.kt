package io.qonversion.nocodes.dto

/**
 * A typed default variable of a No-Code screen, configured in the builder and delivered to
 * the SDK at screen load so it can be read by [key] via
 * [io.qonversion.nocodes.dto.QNoCodeScreen.defaultVariables].
 *
 * [value] keeps its configured native type (see [QScreenVariableValue]) rather than being
 * coerced to a String, matching the declared [type].
 *
 * @property kind what the variable represents — see [Kind].
 * @property key variable name it is addressed by (`variable.<key>` in the builder for custom
 * variables, the slot name for product slots); may contain spaces.
 * @property type configured value type: `"boolean"`, `"string"` or `"number"`.
 * @property value the configured default value, preserving its native type.
 */
data class QScreenVariable(
    val kind: Kind,
    val key: String,
    val type: String,
    val value: QScreenVariableValue,
) {

    /**
     * Kind of a [QScreenVariable] — what it was configured as in the builder.
     * The set may grow in future backend versions; values this SDK version does not know
     * are delivered as [Unknown] instead of failing the screen load.
     */
    enum class Kind {
        /**
         * A Screen Variable authored in the builder's Variables section.
         */
        Custom,

        /**
         * A product slot: [key] is the slot name and [value] is the default Qonversion
         * product id assigned to it.
         */
        Product,

        /**
         * A kind introduced on the backend after this SDK version was released.
         */
        Unknown,
    }
}

/**
 * Typed value of a [QScreenVariable]. Preserves the configured JSON type instead of
 * collapsing everything to a String.
 */
sealed class QScreenVariableValue {
    /**
     * A boolean default value.
     * @property value the configured boolean.
     */
    data class Bool(val value: Boolean) : QScreenVariableValue()

    /**
     * A string default value.
     * @property value the configured string (may be empty).
     */
    data class Str(val value: String) : QScreenVariableValue()

    /**
     * A numeric default value. JSON numbers are normalized to [Double].
     * @property value the configured number.
     */
    data class Num(val value: Double) : QScreenVariableValue()

    /**
     * A `null`/absent configured default.
     */
    object None : QScreenVariableValue()
}
