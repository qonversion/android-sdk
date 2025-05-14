package io.qonversion.nocodes.internal.screen.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import io.qonversion.nocodes.R
import io.qonversion.nocodes.dto.QScreenPresentationStyle
import io.qonversion.nocodes.internal.screen.getScreenTransactionAnimations

class ScreenActivity : FragmentActivity(R.layout.nc_activity_screen) {

    @Suppress("DEPRECATION")
    private val presentationStyle get() = intent.getSerializableExtra(
        INTENT_SCREEN_PRESENTATION_STYLE
    ) as? QScreenPresentationStyle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        if (savedInstanceState == null) {
            showScreen(
                intent.getStringExtra(INTENT_CONTEXT_KEY),
                null,
                false
            )
        }
    }

    override fun finish() {
        super.finish()
        playCloseAnimation()
    }

    internal fun showScreen(contextKey: String?, screenId: String?, addToBackStack: Boolean = true) {
        val args = ScreenFragment.getArguments(contextKey, screenId)
        val fragment = ScreenFragment()
        fragment.arguments = args
        val transaction = supportFragmentManager.beginTransaction()

        if (addToBackStack) {
            transaction
                .setCustomAnimations(
                    R.anim.nc_slide_in_from_left,
                    R.anim.nc_fade_out,
                    R.anim.nc_fade_in,
                    R.anim.nc_slide_out_to_left
                )
                .addToBackStack(null)
        }

        transaction
            .replace(R.id.fragment_container_view, fragment)
            .commit()
    }

    internal fun goBack(): Boolean {
        val isLastScreen = supportFragmentManager.backStackEntryCount == 0
        if (isLastScreen) {
            finish()
        } else {
            supportFragmentManager.popBackStack()
        }
        return isLastScreen
    }

    private fun playCloseAnimation() {
        getScreenTransactionAnimations(presentationStyle)?.let {
            val (openAnimation, closeAnimation) = it
            overridePendingTransition(openAnimation, closeAnimation)
        }
    }

    companion object {
        private const val INTENT_CONTEXT_KEY = "contextKey"
        private const val INTENT_SCREEN_PRESENTATION_STYLE = "screenPresentationStyle"

        fun getCallingIntent(
            context: Context,
            contextKey: String,
            screenPresentationStyle: QScreenPresentationStyle
        ) =
            Intent(context, ScreenActivity::class.java).also {
                it.putExtra(INTENT_CONTEXT_KEY, contextKey)
                it.putExtra(INTENT_SCREEN_PRESENTATION_STYLE, screenPresentationStyle)
            }
    }
}
