package io.qonversion.nocodes.dto

/**
 * A loaded No-Code screen returned from [io.qonversion.nocodes.NoCodes.loadScreen].
 *
 * Exposes only the screen identifiers — the screen content stays internal, as rendering
 * remains the SDK's job via [io.qonversion.nocodes.NoCodes.showScreen].
 *
 * @property id identifier of the screen.
 * @property contextKey the context key of the screen set in the No-Codes builder.
 */
data class QNoCodeScreen(
    val id: String,
    val contextKey: String,
)
