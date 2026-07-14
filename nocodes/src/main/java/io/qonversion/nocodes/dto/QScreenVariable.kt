package io.qonversion.nocodes.dto

/**
 * A No-Code screen variable authored in the builder, delivered to the SDK at screen load so it
 * can be read by [key] after the screen appears.
 *
 * [value] keeps its authored native type (Boolean / String / Number) rather than being coerced
 * to a String, matching the declared [type].
 *
 * @property key variable name it is addressed by (`variable.<key>` in the builder); may contain spaces.
 * @property type authored type: `"boolean"`, `"string"` or `"number"`.
 * @property value the authored default value, preserving its native type (may be null).
 */
data class QScreenVariable(
    val key: String,
    val type: String,
    val value: Any?,
)
