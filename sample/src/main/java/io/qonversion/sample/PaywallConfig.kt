@file:OptIn(ExperimentalQonversionApi::class)

package io.qonversion.sample

import android.graphics.Color
import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigDecoder
import org.json.JSONArray
import org.json.JSONObject

/** The Remote Config context key the Calmly paywall is driven by. */
const val PAYWALL_CONTEXT_KEY = "paywall_config"

/** One purchasable plan rendered as a card. */
data class PaywallProduct(
    val id: String,
    val title: String,
    val priceText: String,
    /** Optional chip drawn on the card, e.g. "BEST VALUE". Absent or JSON `null` means no chip. */
    val badge: String?,
)

/**
 * Everything the paywall needs to render itself.
 *
 * The screen has no hardcoded copy, colors or plans of its own: it is a pure function of this
 * model, so publishing a new release of [PAYWALL_CONTEXT_KEY] is enough to change the product.
 */
data class PaywallConfig(
    val headline: String,
    val subtitle: String,
    /** `#RRGGBB` (or `#AARRGGBB`). Not validated at decode time — see [accentColorOrDefault]. */
    val accentColor: String,
    val ctaText: String,
    val showCountdown: Boolean,
    val countdownSeconds: Int,
    val products: List<PaywallProduct>,
    val highlightProductId: String,
)

/**
 * The defaults shipped inside the binary — what the developer released to the store.
 *
 * The screen renders fully from these before any network call, so a cold start with no
 * connectivity still shows a complete paywall.
 */
val BUNDLED_PAYWALL_CONFIG = PaywallConfig(
    headline = "Find your calm",
    subtitle = "Guided meditations, sleep stories and breathing exercises. Five minutes a day is enough.",
    accentColor = "#7C5CFF",
    ctaText = "Start 7-day free trial",
    showCountdown = false,
    countdownSeconds = 0,
    products = listOf(
        PaywallProduct(
            id = "calmly_monthly",
            title = "Monthly",
            priceText = "$9.99 / month",
            badge = null,
        ),
        PaywallProduct(
            id = "calmly_annual",
            title = "Annual",
            priceText = "$59.99 / year",
            badge = "BEST VALUE",
        ),
    ),
    highlightProductId = "calmly_annual",
)

/** Accent color used when the configured one is missing or not a parseable hex string. */
const val PAYWALL_FALLBACK_ACCENT_COLOR = 0xFF7C5CFF.toInt()

/**
 * Parses [PaywallConfig.accentColor], falling back to [PAYWALL_FALLBACK_ACCENT_COLOR].
 *
 * A broken color is deliberately not a decode failure: rejecting the whole release over one
 * cosmetic field would throw away valid copy and pricing. The screen keeps its default color and
 * reports the bad value instead, which is exactly the input for decode-failure telemetry.
 */
fun PaywallConfig.accentColorOrDefault(): Int = try {
    Color.parseColor(accentColor)
} catch (e: IllegalArgumentException) {
    PAYWALL_FALLBACK_ACCENT_COLOR
}

/** Whether [PaywallConfig.accentColor] would render as configured. */
fun PaywallConfig.hasValidAccentColor(): Boolean = try {
    Color.parseColor(accentColor)
    true
} catch (e: IllegalArgumentException) {
    false
}

/**
 * Decodes the JSON text stored for [PAYWALL_CONTEXT_KEY].
 *
 * Returning `null` rejects the candidate and lets the SDK fall to the next resolution-ladder
 * position (cache, then the bundled defaults file), so this decoder is strict about the fields the
 * screen cannot render without — copy, CTA text and at least one product — and lenient about the
 * rest.
 */
val PaywallConfigDecoder = QRemoteConfigDecoder { rawJson ->
    try {
        val root = JSONObject(rawJson)

        val products = root.optJSONArray("products").toProducts()
        if (products.isEmpty()) return@QRemoteConfigDecoder null

        PaywallConfig(
            headline = root.requiredString("headline") ?: return@QRemoteConfigDecoder null,
            subtitle = root.requiredString("subtitle") ?: return@QRemoteConfigDecoder null,
            // Kept verbatim: an unparseable color degrades at render time, it does not reject.
            accentColor = root.optNullableString("accentColor") ?: BUNDLED_PAYWALL_CONFIG.accentColor,
            ctaText = root.requiredString("ctaText") ?: return@QRemoteConfigDecoder null,
            showCountdown = root.optBoolean("showCountdown", false),
            countdownSeconds = root.optInt("countdownSeconds", 0).coerceAtLeast(0),
            products = products,
            highlightProductId = root.optNullableString("highlightProductId").orEmpty(),
        )
    } catch (e: Exception) {
        // A decoder must never crash the read: any malformed payload is simply not a candidate.
        null
    }
}

private fun JSONArray?.toProducts(): List<PaywallProduct> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        PaywallProduct(
            id = item.requiredString("id") ?: return@mapNotNull null,
            title = item.requiredString("title") ?: return@mapNotNull null,
            priceText = item.requiredString("priceText") ?: return@mapNotNull null,
            badge = item.optNullableString("badge")?.takeIf { it.isNotBlank() },
        )
    }
}

/** A present, non-blank string, or `null` — which callers turn into a rejection. */
private fun JSONObject.requiredString(name: String): String? =
    optNullableString(name)?.takeIf { it.isNotBlank() }

/** Distinguishes a missing member and the JSON literal `null` from the string `"null"`. */
private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

/**
 * Renders the wire shape of this config.
 *
 * The screen logs the bundled default through this so the exact JSON can be pasted into the
 * dashboard as the initial value of [PAYWALL_CONTEXT_KEY] — the decoder above is the only contract
 * between the two, and this keeps both sides written from the same source.
 */
fun PaywallConfig.toWireJson(): JSONObject = JSONObject().apply {
    put("headline", headline)
    put("subtitle", subtitle)
    put("accentColor", accentColor)
    put("ctaText", ctaText)
    put("showCountdown", showCountdown)
    put("countdownSeconds", countdownSeconds)
    put(
        "products",
        JSONArray().apply {
            products.forEach { product ->
                put(
                    JSONObject().apply {
                        put("id", product.id)
                        put("title", product.title)
                        put("priceText", product.priceText)
                        // JSONObject.put(String, null) removes the member, so spell the null out.
                        put("badge", product.badge ?: JSONObject.NULL)
                    },
                )
            }
        },
    )
    put("highlightProductId", highlightProductId)
}
