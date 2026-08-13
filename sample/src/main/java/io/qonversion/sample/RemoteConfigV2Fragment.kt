@file:OptIn(ExperimentalQonversionApi::class)

package io.qonversion.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QUser
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigActivationResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSnapshot
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSubscription
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigUpdate
import com.qonversion.android.sdk.listeners.QonversionUserCallback
import io.qonversion.sample.databinding.FragmentRemoteConfigV2Binding

private const val TAG = "RemoteConfigV2Fragment"

/**
 * Remote Config v2 playground.
 *
 * The v2 pipeline is fetch/activate, not fetch/serve: a fetch only makes a release available and
 * [com.qonversion.android.sdk.QRemoteConfigSnapshots.activate] swaps the whole release into
 * `current` atomically. This screen exercises the full customer journey against a local
 * environment — fetch a release, list every resolved key with its own source/apply policy/
 * metadata, watch live updates through a subscription, and switch identity.
 *
 * The pipeline is dormant unless `App` passed a `QRemoteConfigV2Config` to
 * `QonversionConfig.Builder.setRemoteConfigV2Config`. While dormant every fetch completes with
 * `NotConfigured` and `current` stays empty, which this screen reports as-is rather than hiding.
 */
class RemoteConfigV2Fragment : Fragment() {

    private var _binding: FragmentRemoteConfigV2Binding? = null
    private val binding get() = _binding!!

    private val snapshots get() = Qonversion.shared.remoteConfigSnapshots()

    private var subscription: QRemoteConfigSubscription? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemoteConfigV2Binding.inflate(inflater, container, false)

        binding.recyclerViewValues.layoutManager = LinearLayoutManager(context)
        setupButtons()
        renderEnvironment()
        renderSubscriptionState()
        activate()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // The subscription outlives the view, so it must be released with it.
        unsubscribeFromUpdates()
        _binding = null
    }

    private fun setupButtons() {
        binding.buttonFetchAndActivate.setOnClickListener { fetchAndActivate() }
        binding.buttonFetch.setOnClickListener { fetch() }
        binding.buttonActivate.setOnClickListener { activate() }

        binding.buttonToggleSubscription.setOnClickListener {
            if (subscription == null) subscribeToUpdates() else unsubscribeFromUpdates()
        }

        binding.buttonIdentify.setOnClickListener { identify(DEMO_USER_ID) }
        binding.buttonLogout.setOnClickListener { logout() }
    }

    private fun renderEnvironment() {
        binding.environmentInfo.text = getString(
            R.string.rc_v2_environment_format,
            RC_V2_PLAYGROUND_BASE_URL,
            RC_V2_PLAYGROUND_ENVIRONMENT_UID
        )
    }

    // region SDK calls

    private fun fetchAndActivate() {
        binding.progressBar.visibility = View.VISIBLE
        snapshots.fetchAndActivate { result -> onActivation(result) }
    }

    private fun fetch() {
        binding.progressBar.visibility = View.VISIBLE
        snapshots.fetch { result -> onFetch(result) }
    }

    private fun activate() {
        binding.progressBar.visibility = View.VISIBLE
        snapshots.activate { result -> onActivation(result) }
    }

    /**
     * Callbacks are guaranteed to arrive on the main thread exactly once, but not that the view is
     * still alive, so every handler goes through the nullable binding.
     */
    private fun onFetch(result: QRemoteConfigFetchResult) {
        _binding?.let { b ->
            b.progressBar.visibility = View.GONE
            b.statusText.text = getString(
                R.string.rc_v2_fetch_status_format,
                result.status.name,
                result.snapshot.releaseNumber.toString()
            )
            // Fetching an on-next-activate release must not make the candidate look current.
            // An immediate-policy release is already current by callback time, even when this
            // screen is not subscribed to update events, so it is safe and necessary to render it.
            val activatedImmediately = result.snapshot.contextKeys.any { contextKey ->
                result.snapshot.rawValue(contextKey)?.applyPolicy ==
                    com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigApplyPolicy.Immediate
            }
            if (activatedImmediately) renderSnapshot(result.snapshot)
        }
    }

    private fun onActivation(result: QRemoteConfigActivationResult) {
        _binding?.let { b ->
            b.progressBar.visibility = View.GONE
            val fetchStatus = result.fetchStatus?.name ?: getString(R.string.rc_v2_no_fetch)
            b.statusText.text = getString(
                R.string.rc_v2_activation_status_format,
                fetchStatus,
                result.changed.toString()
            )
            renderSnapshot(result.snapshot)
        }
    }

    private fun subscribeToUpdates() {
        subscription = snapshots.subscribeOnConfigUpdate { update -> onConfigUpdated(update) }
        renderSubscriptionState()
        Toast.makeText(context, getString(R.string.rc_v2_subscribed), Toast.LENGTH_SHORT).show()
    }

    private fun unsubscribeFromUpdates() {
        subscription?.remove()
        subscription = null
        renderSubscriptionState()
    }

    /**
     * Fires whenever a release becomes current — either an explicit activate or an immediate-policy
     * release admitted by the SDK on its own.
     */
    private fun onConfigUpdated(update: QRemoteConfigUpdate) {
        _binding?.let { b ->
            b.updateText.text = getString(
                R.string.rc_v2_update_format,
                update.snapshot.releaseNumber.toString(),
                update.changedKeys.sorted().joinToString(", ").ifEmpty {
                    getString(R.string.rc_v2_no_changed_keys)
                }
            )
            b.updateText.visibility = View.VISIBLE
            renderSnapshot(update.snapshot)
        }
    }

    private fun identify(userId: String) {
        binding.progressBar.visibility = View.VISIBLE
        Qonversion.shared.identify(userId, object : QonversionUserCallback {
            override fun onSuccess(user: QUser) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                    b.identityText.text = getString(R.string.rc_v2_identity_format, user.identityId ?: user.qonversionId)
                }
                // Identity changes the resolution scope, so the previous release no longer applies.
                Toast.makeText(context, getString(R.string.rc_v2_identified, userId), Toast.LENGTH_SHORT).show()
            }

            override fun onError(error: QonversionError) {
                _binding?.progressBar?.visibility = View.GONE
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun logout() {
        Qonversion.shared.logout()
        binding.identityText.text = getString(R.string.rc_v2_identity_anonymous)
        Toast.makeText(context, getString(R.string.rc_v2_logged_out), Toast.LENGTH_SHORT).show()
    }

    // endregion

    // region rendering

    private fun renderSnapshot(snapshot: QRemoteConfigSnapshot) {
        val binding = _binding ?: return

        // contextKeys also contains server tombstones used to suppress removed values. Count and
        // display only keys which remain readable from the server/fallback resolution ladder.
        val values = snapshot.contextKeys.sorted().mapNotNull { contextKey ->
            snapshot.rawValue(contextKey)?.let { value -> ResolvedEntry(contextKey, value) }
        }

        // A fallback-only snapshot carries no release: releaseNumber is 0 and releaseUid is empty.
        binding.releaseInfo.text = if (snapshot.releaseNumber == 0L) {
            getString(R.string.rc_v2_no_release)
        } else {
            getString(
                R.string.rc_v2_release_format,
                snapshot.releaseNumber.toString(),
                snapshot.releaseUid,
                values.size.toString()
            )
        }

        if (values.isEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.recyclerViewValues.visibility = View.GONE
        } else {
            binding.emptyStateText.visibility = View.GONE
            binding.recyclerViewValues.visibility = View.VISIBLE
            binding.recyclerViewValues.adapter = RemoteConfigV2Adapter(values)
        }
    }

    private fun renderSubscriptionState() {
        val binding = _binding ?: return
        val subscribed = subscription != null

        binding.subscriptionIndicator.setBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                if (subscribed) R.color.colorGreen else R.color.colorGray
            )
        )
        binding.subscriptionText.setText(
            if (subscribed) R.string.rc_v2_subscription_active else R.string.rc_v2_subscription_inactive
        )
        binding.buttonToggleSubscription.setText(
            if (subscribed) R.string.rc_v2_unsubscribe else R.string.rc_v2_subscribe
        )
    }

    // endregion

    companion object {
        private const val DEMO_USER_ID = "test-user-1"
    }
}
