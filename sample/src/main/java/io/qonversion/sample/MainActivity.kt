package io.qonversion.sample

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.redemption.RedemptionResult
import com.qonversion.android.sdk.listeners.QonversionRedemptionCallback
import io.qonversion.sample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.findNavController()

        // Setup AppBar with top-level destinations (bottom nav items)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.productsFragment,
                R.id.entitlementsFragment,
                R.id.userFragment,
                R.id.otherFragment
            )
        )

        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
        binding.bottomNav.setupWithNavController(navController)

        // Hide/show bottom nav and toolbar based on destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isTopLevel = appBarConfiguration.topLevelDestinations.contains(destination.id)
            binding.bottomNav.visibility = if (isTopLevel) View.VISIBLE else View.GONE

            // Show toolbar title
            binding.toolbar.title = destination.label
        }

        // Web2App (DEV-847) — handle redemption deep links.
        handleRedemptionIntent(intent)
        handleReissueIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRedemptionIntent(intent)
        handleReissueIntent(intent)
    }

    private fun handleReissueIntent(intent: Intent?) {
        // Triggered via `adb shell am start -a qontest.REISSUE` to exercise
        // the reissue dialog without needing a UI button.
        if (intent?.action != "qontest.REISSUE") return
        Log.i("Web2App", "presentReissueUI triggered via intent")
        Qonversion.shared.presentReissueUI(this) { success ->
            Log.i("Web2App", "Reissue completed: success=$success")
        }
    }

    private fun handleRedemptionIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        // Accept ONLY the verified https App Link on the canonical host. Any
        // installed app can claim a custom (`qonversion://`) scheme — or a plain
        // `http`/foreign-host link — and hijack the single-use redemption token,
        // so reference code must not whitelist those. (The SDK also re-checks
        // scheme + host before any network call; this is defense-in-depth.)
        val ok = uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("screens.qonversion.io", ignoreCase = true) &&
                uri.pathSegments.firstOrNull() == "r"
        if (!ok) {
            return
        }
        // Do NOT log/show the full Uri — the redemption token is in the path.
        Log.i("Web2App", "handleRedemptionLink: host=${uri.host}")
        Toast.makeText(this, "Redemption link tapped", Toast.LENGTH_SHORT).show()
        Qonversion.shared.handleRedemptionLink(uri, object : QonversionRedemptionCallback {
            override fun onResult(result: RedemptionResult) {
                Log.i("Web2App", "Redemption result: $result")
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Redemption: $result",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    companion object {
        fun getCallingIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java)
        }
    }
}
