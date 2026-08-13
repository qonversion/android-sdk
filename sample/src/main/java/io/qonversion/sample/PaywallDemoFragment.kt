@file:OptIn(ExperimentalQonversionApi::class)

package io.qonversion.sample

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigActivationResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigApplyPolicy
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchStatus
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSnapshot
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSource
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSubscription
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigUpdate
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigValue
import io.qonversion.sample.databinding.FragmentPaywallDemoBinding
import io.qonversion.sample.databinding.ItemPaywallProductBinding
import org.json.JSONObject
import java.util.Locale

private const val TAG = "PaywallDemo"

private const val COUNTDOWN_TICK_MS = 1000L
private const val COUNTDOWN_BAR_MAX = 1000
private const val SECONDS_PER_MINUTE = 60

/**
 * A paywall for the fictional meditation app "Calmly", rendered entirely from one Remote Config
 * key — [PAYWALL_CONTEXT_KEY].
 *
 * Nothing on this screen is hardcoded UI copy: headline, subtitle, accent color, plans, badges,
 * CTA text and the countdown all come from a [PaywallConfig]. That makes the screen a faithful
 * stand-in for a real integration, where shipping a config release changes the product without
 * shipping an app.
 *
 * The three states an integrator actually has to handle are all visible here:
 *
 * 1. **Bundled default** — [BUNDLED_PAYWALL_CONFIG] renders before any network call, so a cold
 *    start with no connectivity still shows a complete paywall.
 * 2. **Resolved** — the typed read `snapshot.value(key, decoder)` reports where the value came
 *    from ([QRemoteConfigSource]) and under which [QRemoteConfigApplyPolicy], both shown verbatim
 *    in the source line at the bottom.
 * 3. **Pending** — a fetched release the app has not applied yet. The SDK exposes no explicit
 *    "pending release" handle, so this screen derives it: a plain `fetch` completes with the *last
 *    fetched* release, and when its number is ahead of the activated one and it changes this key,
 *    the release is waiting for an activation.
 */
class PaywallDemoFragment : Fragment() {

    private var _binding: FragmentPaywallDemoBinding? = null
    private val binding get() = _binding!!

    private val snapshots get() = Qonversion.shared.remoteConfigSnapshots()

    private var subscription: QRemoteConfigSubscription? = null

    /** The config currently on screen — kept so a re-render of the same values leaves it alone. */
    private var renderedConfig: PaywallConfig? = null

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownTotalSeconds = 0
    private var countdownRemainingSeconds = 0

    private val countdownTick = object : Runnable {
        override fun run() {
            if (_binding == null) return
            countdownRemainingSeconds = (countdownRemainingSeconds - 1).coerceAtLeast(0)
            renderCountdownValue()
            if (countdownRemainingSeconds > 0) countdownHandler.postDelayed(this, COUNTDOWN_TICK_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaywallDemoBinding.inflate(inflater, container, false)

        logBundledDefaultOnce()
        setupButtons()

        // First paint is the bundled default and touches no SDK state: `current` is guarded by the
        // SDK's read-before-activate assert (debug builds crash on it — proven live on the
        // emulator), so nothing may be read until an activate() has run. The paywall is still
        // complete on screen before the first byte is sent.
        render(BUNDLED_PAYWALL_CONFIG, resolved = null, releaseNumber = 0L)

        // Live updates: an activation performed elsewhere — including the SDK's own immediate-policy
        // swap — re-renders this screen in place.
        subscription = snapshots.subscribeOnConfigUpdate { update -> onConfigUpdated(update) }

        // activate() is the integrator's handshake with the read guard: it applies whatever is
        // already fetched (or nothing) and only THEN is `current` legal to read. All real
        // rendering flows through its callback and the subscription above.
        snapshots.activate { _ ->
            val activated = snapshots.current
            if (_binding != null) {
                renderFromSnapshot(activated)
                syncOnOpen(activated.releaseNumber)
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdownHandler.removeCallbacks(countdownTick)
        // The subscription outlives the view, so it must be released with it.
        subscription?.remove()
        subscription = null
        _binding = null
    }

    private fun setupButtons() {
        binding.buttonRefresh.setOnClickListener { refresh() }
        binding.buttonActivateNow.setOnClickListener { activatePending() }
        binding.buttonCta.setOnClickListener {
            Toast.makeText(context, getString(R.string.paywall_cta_toast), Toast.LENGTH_SHORT).show()
        }
    }

    // region SDK calls

    /** The refresh affordance: fetch a release and apply it in one step. */
    private fun refresh() {
        binding.progressBar.visibility = View.VISIBLE
        snapshots.fetchAndActivate { result -> onActivation(result) }
    }

    private fun activatePending() {
        binding.progressBar.visibility = View.VISIBLE
        snapshots.activate { result -> onActivation(result) }
    }

    /**
     * What the screen does the moment it opens, and the one place the fetch/activate split is a
     * product decision rather than a mechanism.
     *
     * With nothing activated there is no user-visible state to protect, so a release is fetched and
     * applied straight away. With a release already on screen the fetch deliberately stops short of
     * activating: swapping copy and pricing under someone who is reading them is exactly what the
     * on-next-activate policy exists to prevent, so a newer release is announced instead.
     *
     * A plain fetch completes with the *last fetched* release rather than the activated one, which
     * is what makes a pending release observable at all.
     */
    private fun syncOnOpen(activatedReleaseNumber: Long) {
        binding.progressBar.visibility = View.VISIBLE
        if (activatedReleaseNumber == 0L) {
            snapshots.fetchAndActivate { result -> onActivation(result) }
        } else {
            snapshots.fetch { result -> onProbeResult(result) }
        }
    }

    /**
     * Callbacks arrive on the main thread exactly once, but carry no guarantee that the view is
     * still alive, so every handler goes through the nullable binding.
     */
    private fun onActivation(result: QRemoteConfigActivationResult) {
        _binding?.let { b ->
            b.progressBar.visibility = View.GONE
            renderFromSnapshot(result.snapshot)
            hidePending()
            reportFetchStatus(result.fetchStatus)
        }
    }

    private fun onProbeResult(result: QRemoteConfigFetchResult) {
        val b = _binding ?: return
        b.progressBar.visibility = View.GONE

        val fetched = result.snapshot
        // Re-read the activated release here rather than trusting what was rendered: an
        // immediate-policy release may have been swapped in — and re-rendered through the
        // subscription — while this fetch was in flight.
        val activated = snapshots.current

        val pendingRaw = fetched.rawValue(PAYWALL_CONTEXT_KEY)?.value
        val activatedRaw = activated.rawValue(PAYWALL_CONTEXT_KEY)?.value

        // A newer release that does not touch this key is not "pending" as far as this screen is
        // concerned, so it is not announced.
        if (fetched.releaseNumber > activated.releaseNumber && pendingRaw != activatedRaw) {
            showPending(fetched)
        } else {
            hidePending()
        }
    }

    /**
     * Fires whenever a release becomes current — an explicit activate, or an immediate-policy
     * release the SDK admitted on its own. The latter is what makes the screen change while the
     * user is looking at it.
     */
    private fun onConfigUpdated(update: QRemoteConfigUpdate) {
        if (_binding == null) return
        if (!update.changedKeys.contains(PAYWALL_CONTEXT_KEY)) return

        Log.i(TAG, "Config update: release ${update.snapshot.releaseNumber}, $PAYWALL_CONTEXT_KEY changed")
        renderFromSnapshot(update.snapshot)
        hidePending()
    }

    private fun reportFetchStatus(status: QRemoteConfigFetchStatus?) {
        if (status == null || status == QRemoteConfigFetchStatus.Fetched ||
            status == QRemoteConfigFetchStatus.NotModified
        ) {
            return
        }
        Toast.makeText(
            context,
            getString(R.string.paywall_fetch_status_format, status.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    // endregion

    // region rendering

    /**
     * The single typed read this whole screen is built on.
     *
     * A decoder that returns null rejects a candidate and the SDK falls to the next
     * resolution-ladder position; when every position is rejected — or the key is simply unknown —
     * the read answers null and the bundled default takes over.
     */
    private fun renderFromSnapshot(snapshot: QRemoteConfigSnapshot) {
        val resolved = snapshot.value(PAYWALL_CONTEXT_KEY, PaywallConfigDecoder)

        if (resolved == null && snapshot.contextKeys.contains(PAYWALL_CONTEXT_KEY)) {
            // The key exists in the release but nothing on the ladder survived the decoder. Worth
            // saying out loud: this is the shape a decode-failure looks like from the app side.
            Log.w(TAG, "`$PAYWALL_CONTEXT_KEY` is present in release ${snapshot.releaseNumber} but did not decode")
        }

        render(resolved?.value ?: BUNDLED_PAYWALL_CONFIG, resolved, snapshot.releaseNumber)
    }

    private fun render(
        config: PaywallConfig,
        resolved: QRemoteConfigValue<PaywallConfig>?,
        releaseNumber: Long,
    ) {
        val binding = _binding ?: return

        if (!config.hasValidAccentColor()) {
            Log.w(TAG, "accentColor \"${config.accentColor}\" is not a parseable hex color, keeping the default")
        }
        val accent = config.accentColorOrDefault()

        binding.headline.text = config.headline
        binding.subtitle.text = config.subtitle

        binding.buttonCta.text = config.ctaText
        binding.buttonCta.backgroundTintList = ColorStateList.valueOf(accent)

        renderProducts(config, accent)
        renderCountdown(config, accent)
        renderSource(config, resolved, releaseNumber)

        renderedConfig = config
    }

    private fun renderProducts(config: PaywallConfig, accent: Int) {
        val container = binding.productsContainer
        val inflater = LayoutInflater.from(container.context)
        val spacing = container.context.dp(PRODUCT_SPACING_DP)

        container.removeAllViews()
        config.products.forEachIndexed { index, product ->
            val item = ItemPaywallProductBinding.inflate(inflater, container, false)

            item.productTitle.text = product.title
            item.productPrice.text = product.priceText

            val highlighted = product.id == config.highlightProductId
            item.root.background = cardBackground(container.context, accent, highlighted)

            if (product.badge == null) {
                item.productBadge.visibility = View.GONE
            } else {
                item.productBadge.visibility = View.VISIBLE
                item.productBadge.text = product.badge
                item.productBadge.background = badgeBackground(container.context, accent)
            }

            (item.root.layoutParams as? LinearLayout.LayoutParams)?.topMargin =
                if (index == 0) 0 else spacing

            container.addView(item.root)
        }
    }

    /** The highlight ring: a thicker stroke in the accent color on the promoted plan. */
    private fun cardBackground(context: Context, accent: Int, highlighted: Boolean): GradientDrawable {
        // Drawables loaded from resources share a constant state, so each card must mutate its own.
        val background = ContextCompat.getDrawable(context, R.drawable.paywall_product_card)
            ?.mutate() as GradientDrawable
        background.setStroke(
            context.dp(if (highlighted) HIGHLIGHT_STROKE_DP else PLAIN_STROKE_DP),
            if (highlighted) accent else ContextCompat.getColor(context, R.color.colorDivider),
        )
        return background
    }

    private fun badgeBackground(context: Context, accent: Int): GradientDrawable {
        val background = ContextCompat.getDrawable(context, R.drawable.paywall_badge_chip)
            ?.mutate() as GradientDrawable
        background.setColor(accent)
        return background
    }

    private fun renderCountdown(config: PaywallConfig, accent: Int) {
        val binding = _binding ?: return

        val enabled = config.showCountdown && config.countdownSeconds > 0
        if (!enabled) {
            countdownHandler.removeCallbacks(countdownTick)
            countdownTotalSeconds = 0
            countdownRemainingSeconds = 0
            binding.countdownContainer.visibility = View.GONE
            return
        }

        binding.countdownContainer.visibility = View.VISIBLE
        binding.countdownText.setTextColor(accent)
        binding.countdownBar.progressTintList = ColorStateList.valueOf(accent)

        // Restart only when the countdown itself was re-configured; an unrelated re-render must not
        // silently give the user their time back.
        val previous = renderedConfig
        val unchanged = previous != null &&
            previous.showCountdown == config.showCountdown &&
            previous.countdownSeconds == config.countdownSeconds
        if (unchanged && countdownTotalSeconds == config.countdownSeconds) {
            renderCountdownValue()
            return
        }

        countdownHandler.removeCallbacks(countdownTick)
        countdownTotalSeconds = config.countdownSeconds
        countdownRemainingSeconds = config.countdownSeconds
        renderCountdownValue()
        countdownHandler.postDelayed(countdownTick, COUNTDOWN_TICK_MS)
    }

    private fun renderCountdownValue() {
        val binding = _binding ?: return

        binding.countdownText.text = if (countdownRemainingSeconds > 0) {
            getString(R.string.paywall_countdown_format, formatDuration(countdownRemainingSeconds))
        } else {
            getString(R.string.paywall_countdown_expired)
        }

        binding.countdownBar.max = COUNTDOWN_BAR_MAX
        binding.countdownBar.progress = if (countdownTotalSeconds <= 0) {
            0
        } else {
            countdownRemainingSeconds * COUNTDOWN_BAR_MAX / countdownTotalSeconds
        }
    }

    /**
     * The unobtrusive provenance line: which ladder position answered, under which release, apply
     * policy, and the experiment/group the release attributes the value to.
     */
    private fun renderSource(
        config: PaywallConfig,
        resolved: QRemoteConfigValue<PaywallConfig>?,
        releaseNumber: Long,
    ) {
        val binding = _binding ?: return

        val text = StringBuilder()
        if (resolved == null) {
            text.append(getString(R.string.paywall_source_local))
        } else {
            text.append(
                getString(
                    R.string.paywall_source_format,
                    getString(resolved.source.label()),
                    releaseNumber.toString(),
                )
            )
            text.append(getString(R.string.paywall_source_segment_format, getString(resolved.applyPolicy.label())))
            resolved.metadataJson?.experimentSegment()?.let { segment ->
                text.append(getString(R.string.paywall_source_segment_format, segment))
            }
        }
        if (!config.hasValidAccentColor()) {
            text.append(getString(R.string.paywall_source_bad_color_format, config.accentColor))
        }

        binding.sourceText.text = text
    }

    private fun showPending(pending: QRemoteConfigSnapshot) {
        val binding = _binding ?: return

        val policy = pending.value(PAYWALL_CONTEXT_KEY, PaywallConfigDecoder)?.applyPolicy
            ?: QRemoteConfigApplyPolicy.OnNextActivate

        binding.pendingContainer.visibility = View.VISIBLE
        binding.pendingText.text = getString(
            R.string.paywall_pending_format,
            pending.releaseNumber.toString(),
            getString(policy.label()),
        )
    }

    private fun hidePending() {
        _binding?.pendingContainer?.visibility = View.GONE
    }

    // endregion

    /**
     * Prints the wire shape of the bundled default once per process, so it can be pasted into the
     * dashboard as the initial value of [PAYWALL_CONTEXT_KEY].
     */
    private fun logBundledDefaultOnce() {
        if (bundledDefaultLogged) return
        bundledDefaultLogged = true
        // Android's JSONStringer escapes forward slashes, which is valid JSON but noisy to paste;
        // unescaping them keeps the logged text identical to what belongs in the dashboard field.
        val json = BUNDLED_PAYWALL_CONFIG.toWireJson().toString(JSON_INDENT).replace("\\/", "/")
        Log.i(TAG, "Bundled default for `$PAYWALL_CONTEXT_KEY` — paste as the key's initial value:\n$json")
    }

    private fun QRemoteConfigSource.label(): Int = when (this) {
        QRemoteConfigSource.Server -> R.string.paywall_source_server
        QRemoteConfigSource.Cache -> R.string.paywall_source_cache
        QRemoteConfigSource.Fallback -> R.string.paywall_source_fallback
    }

    private fun QRemoteConfigApplyPolicy.label(): Int = when (this) {
        QRemoteConfigApplyPolicy.Immediate -> R.string.paywall_policy_immediate
        QRemoteConfigApplyPolicy.OnNextActivate -> R.string.paywall_policy_on_next_activate
    }

    /** Metadata is app-defined JSON; surface the experiment/group pair when the release carries it. */
    private fun String.experimentSegment(): String? = try {
        val metadata = JSONObject(this)
        val experiment = metadata.optString("experiment").takeIf { it.isNotEmpty() }
        val group = metadata.optString("group").takeIf { it.isNotEmpty() }
        when {
            experiment != null && group != null -> getString(R.string.paywall_experiment_format, experiment, group)
            experiment != null -> experiment
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun formatDuration(totalSeconds: Int): String = String.format(
        Locale.US,
        "%02d:%02d",
        totalSeconds / SECONDS_PER_MINUTE,
        totalSeconds % SECONDS_PER_MINUTE,
    )

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PRODUCT_SPACING_DP = 10
        private const val PLAIN_STROKE_DP = 1
        private const val HIGHLIGHT_STROKE_DP = 2
        private const val JSON_INDENT = 2

        /** Per-process, so re-opening the screen does not spam the log the human is reading. */
        private var bundledDefaultLogged = false
    }
}
