package io.qonversion.nocodes.internal.screen.view

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import io.qonversion.nocodes.internal.di.DependenciesAssembly

/**
 * Intercepts AndroidX FragmentManager's reflective fragment instantiation so we can
 * substitute a safe stand-in when [ScreenFragment] would otherwise crash.
 *
 * The crash scenario:
 * 1. The user is on a no-code screen, then backgrounds the app.
 * 2. Android kills the host process under memory pressure.
 * 3. The user returns. Android creates a new process and restores the task. The top of
 *    the back stack is [ScreenActivity], so it is created before any other activity.
 * 4. Inside [ScreenActivity.onCreate], `super.onCreate(savedInstanceState)` triggers
 *    FragmentManager.restoreSaveState, which reflectively constructs [ScreenFragment]
 *    via its no-arg constructor. The fragment's property initializers read
 *    `DependenciesAssembly.instance`, but the host app has not had a chance to call
 *    `NoCodes.initialize` yet, so the lateinit throws.
 *
 * To prevent that, [ScreenActivity] installs this factory before super.onCreate.
 * If the assembly is not yet initialized when AndroidX asks for a [ScreenFragment]
 * instance, we return a [FinishingStubFragment] that closes the activity instead.
 *
 * For every other fragment class name we delegate to the default factory so AndroidX's
 * own internal fragments (lifecycle dispatchers etc.) are unaffected.
 */
internal class NoCodesFragmentFactory : FragmentFactory() {

    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        if (className == ScreenFragment::class.java.name &&
            !DependenciesAssembly.isInstanceInitialized()
        ) {
            return FinishingStubFragment()
        }
        return super.instantiate(classLoader, className)
    }
}
