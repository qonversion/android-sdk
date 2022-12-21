package com.qonversion.android.sdk.automations.mvp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.qonversion.android.sdk.R

class ScreenActivity : FragmentActivity(R.layout.q_activity_screen) {
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

    @Deprecated("")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        super.onBackPressed()
        playCloseAnimation()
    }

    private fun playCloseAnimation() {
        overridePendingTransition(R.anim.slide_in_from_bottom, R.anim.slide_out_to_bottom)
    }

    companion object {
        private const val INTENT_HTML_PAGE = "htmlPage"
        private const val INTENT_SCREEN_ID = "screenId"

        fun getCallingIntent(context: Context, screenId: String, htmlPage: String) =
            Intent(context, ScreenActivity::class.java).also {
                it.putExtra(INTENT_SCREEN_ID, screenId)
                it.putExtra(INTENT_HTML_PAGE, htmlPage)
            }
    }
}
