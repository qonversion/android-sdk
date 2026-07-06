package io.qonversion.nocodes.internal.screen.view

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.qonversion.nocodes.internal.di.DependenciesAssembly
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the process-death restoration path. Verifies that
 * [NoCodesFragmentFactory] substitutes a [FinishingStubFragment] for [ScreenFragment]
 * when [DependenciesAssembly.instance] is uninitialized, and otherwise delegates to the
 * default [androidx.fragment.app.FragmentFactory] behavior.
 *
 * Other instrumentation tests in this module initialize [DependenciesAssembly.instance]
 * and the lateinit static persists for the lifetime of the test process. To make this
 * test order-independent we forcibly clear the backing field via reflection in
 * [Before], and restore it in [After].
 */
@RunWith(AndroidJUnit4::class)
internal class NoCodesFragmentFactoryTest {

    private val factory = NoCodesFragmentFactory()
    private val classLoader: ClassLoader = NoCodesFragmentFactoryTest::class.java.classLoader!!

    private val instanceField =
        DependenciesAssembly::class.java.getDeclaredField("instance").apply { isAccessible = true }

    private var savedInstance: DependenciesAssembly? = null

    @Before
    fun clearInstance() {
        savedInstance = instanceField.get(null) as DependenciesAssembly?
        instanceField.set(null, null)
        // Sanity check that our reflection actually changed observable state.
        assertFalse(
            "Expected DependenciesAssembly.instance to read as uninitialized after reflection clear",
            DependenciesAssembly.isInstanceInitialized()
        )
    }

    @After
    fun restoreInstance() {
        instanceField.set(null, savedInstance)
    }

    @Test
    fun substitutesScreenFragmentWhenDiUninitialized() {
        val fragment = factory.instantiate(classLoader, ScreenFragment::class.java.name)

        assertEquals(FinishingStubFragment::class.java, fragment.javaClass)
    }

    @Test
    fun delegatesToSuperForUnrelatedFragments() {
        val unrelated = androidx.fragment.app.Fragment::class.java.name

        val fragment = factory.instantiate(classLoader, unrelated)

        assertEquals(androidx.fragment.app.Fragment::class.java, fragment.javaClass)
        assertNotEquals(FinishingStubFragment::class.java, fragment.javaClass)
    }
}
