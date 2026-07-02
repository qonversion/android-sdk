package io.qonversion.nocodes.internal.screen.view

import android.os.Bundle
import androidx.fragment.app.Fragment

/**
 * Drop-in replacement for [ScreenFragment] used only by [NoCodesFragmentFactory] when
 * AndroidX FragmentManager tries to recreate a saved [ScreenFragment] before
 * `NoCodes.initialize` has run again in this process (see [NoCodesFragmentFactory] for
 * the full scenario).
 *
 * It does no work other than finishing the host activity in [onCreate]. After the
 * activity is gone, Android falls back to the next entry in the task back stack
 * (typically the host app's main activity). Once the host re-runs `NoCodes.initialize`,
 * the no-code screen can be shown again normally via `NoCodes.showScreen`.
 *
 * Must be a top-level class with a public no-arg constructor: AndroidX
 * FragmentManager instantiates fragments reflectively, and applies its own keep rules
 * to discovered Fragment subclasses, but only when reachable from the manifest.
 * Because this class is reached through code (the factory) rather than the manifest,
 * we add an explicit ProGuard keep rule in nocodes/consumer-rules.pro.
 */
class FinishingStubFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.finish()
    }
}
