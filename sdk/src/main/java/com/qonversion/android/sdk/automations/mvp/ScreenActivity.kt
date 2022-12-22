package com.qonversion.android.sdk.automations.mvp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.qonversion.android.sdk.R
import com.qonversion.android.sdk.automations.dto.QScreenPresentationStyle
import com.qonversion.android.sdk.automations.internal.getScreenTransactionAnimations

class ScreenActivity : FragmentActivity(R.layout.q_activity_screen) {
    private val presentationStyle get() = intent.getSerializableExtra(
        INTENT_SCREEN_PRESENTATION_STYLE
    ) as? QScreenPresentationStyle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val args = ScreenFragment.getArguments(
                intent.getStringExtra(INTENT_SCREEN_ID),
                intent.getStringExtra(INTENT_HTML_PAGE)
            )
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<ScreenFragment>(R.id.fragment_container_view, null, args)
            }
        }
    }

    override fun finish() {
        super.finish()
        playCloseAnimation()
    }

    private fun playCloseAnimation() {
        getScreenTransactionAnimations(presentationStyle)?.let {
            val (openAnimation, closeAnimation) = it
            overridePendingTransition(openAnimation, closeAnimation)
        }
    }

    companion object {
        private const val INTENT_HTML_PAGE = "htmlPage"
        private const val INTENT_SCREEN_ID = "screenId"
        private const val INTENT_SCREEN_PRESENTATION_STYLE = "screenPresentationStyle"

        fun getCallingIntent(
            context: Context,
            screenId: String,
            htmlPage: String,
            screenPresentationStyle: QScreenPresentationStyle
        ) =
            Intent(context, ScreenActivity::class.java).also {
                it.putExtra(INTENT_SCREEN_ID, screenId)
                it.putExtra(INTENT_HTML_PAGE, htmlPage)
                it.putExtra(INTENT_SCREEN_PRESENTATION_STYLE, screenPresentationStyle)
            }
    }
}
