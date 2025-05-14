package io.qonversion.sample

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import io.qonversion.nocodes.NoCodes
import io.qonversion.sample.databinding.FragmentHomeBinding
import kotlin.system.exitProcess
import java.util.*

private const val TAG = "HomeFragment"
private const val CLICK_TIMEOUT = 500L // 5 seconds
private const val REQUIRED_CLICKS = 5

class HomeFragment : Fragment() {

    lateinit var binding: FragmentHomeBinding
    private var clickCount = 0
    private var lastClickTime = 0L
    private val clickHandler = Handler(Looper.getMainLooper())
    private val clickRunnable = Runnable { resetClickCount() }

    private val subscriptionProductId = "weekly"
    private val inAppProductId = "in_app"
    private val entitlementIdForSubscription = "plus"
    private val entitlementIdForInApp = "standart"
    private val entitlementsUpdateListener = getEntitlementsUpdateListener()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeBinding.inflate(inflater)

        setupImageViewClickListener()
        setupQonversion()
        setupButtons()

        return binding.root
    }

    private fun setupImageViewClickListener() {
        binding.imageView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > CLICK_TIMEOUT) {
                resetClickCount()
            }

            clickCount++
            lastClickTime = currentTime

            clickHandler.removeCallbacks(clickRunnable)
            clickHandler.postDelayed(clickRunnable, CLICK_TIMEOUT)

            if (clickCount >= REQUIRED_CLICKS) {
                showConfigurationDialog()
                resetClickCount()
            }
        }
    }

    private fun resetClickCount() {
        clickCount = 0
        lastClickTime = 0
    }

    private fun showConfigurationDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_configuration, null)
        val projectKeyInput = dialogView.findViewById<EditText>(R.id.projectKeyInput)
        val apiRadioGroup = dialogView.findViewById<RadioGroup>(R.id.apiRadioGroup)
        val customUrlInput = dialogView.findViewById<EditText>(R.id.customUrlInput)

        // Initially hide custom URL input
        customUrlInput.visibility = View.GONE

        apiRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            customUrlInput.visibility = if (checkedId == R.id.radioCustom) View.VISIBLE else View.GONE
        }

        val currentProjectKey = getProjectKey(requireContext(), "")
        val currentApiUrl = getApiUrl(requireContext())

        // Set current values to inputs
        projectKeyInput.setText(currentProjectKey)
        if (currentApiUrl != null) {
            apiRadioGroup.check(R.id.radioCustom)
            customUrlInput.setText(currentApiUrl)
            customUrlInput.visibility = View.VISIBLE
        } else {
            apiRadioGroup.check(R.id.radioProduction)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Qonversion configuration")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Apply") { _, _ ->
                val projectKey = projectKeyInput.text.toString()
                val apiUrl = customUrlInput.takeIf {
                    apiRadioGroup.checkedRadioButtonId == R.id.radioCustom
                }?.text?.toString()
                storeQonversionPrefs(requireContext(), projectKey, apiUrl)

                // Close the app
                exitProcess(0)
            }
            .setNeutralButton("Reset") { _, _ ->
                // Clear configuration
                storeQonversionPrefs(requireContext(), "", null)

                // Close the app
                exitProcess(0)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupQonversion() {
        Qonversion.shared.setEntitlementsUpdateListener(entitlementsUpdateListener)

        Qonversion.shared.products(callback = object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) {
                updateContent(products)
                Qonversion.shared.checkEntitlements(getEntitlementsCallback())
            }

            override fun onError(error: QonversionError) {
                showLoading(false)
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun setupButtons() {
        binding.buttonSubscribe.setOnClickListener {
            purchase(subscriptionProductId)
        }

        binding.buttonInApp.setOnClickListener {
            purchase(inAppProductId)
        }

        binding.buttonRestore.setOnClickListener {
            showLoading(true)
            Qonversion.shared.restore(getEntitlementsCallback())
        }

        binding.buttonPaywall.setOnClickListener {
            val builder = AlertDialog.Builder(requireContext())
            val input = EditText(requireContext())
            input.hint = "Context key"

            val inputContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val padding = resources.getDimensionPixelSize(R.dimen.dialog_input_margin)
                setPadding(padding, padding, padding, padding)
                addView(input, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }

            builder.setTitle("Enter context key")
            builder.setView(inputContainer)

            builder.setPositiveButton("Show") { _, _ ->
                val contextKey = input.text.toString()
                if (contextKey.isNotEmpty()) {
                    NoCodes.shared.showScreen(contextKey)
                } else {
                    Toast.makeText(requireContext(), "Please enter a context key", Toast.LENGTH_SHORT).show()
                }
            }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }

            builder.show()
        }

        binding.buttonLogout.setOnClickListener {
            Firebase.auth.signOut()
            Qonversion.shared.logout()
            goToAuth()
        }
    }

    private fun getEntitlementsUpdateListener() = object : QEntitlementsUpdateListener {
        override fun onEntitlementsUpdated(entitlements: Map<String, QEntitlement>) {
            // handle updated entitlements here
        }
    }

    private fun getEntitlementsCallback(): QonversionEntitlementsCallback {
        return object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                showLoading(false)
                handleEntitlements(entitlements)
            }

            override fun onError(error: QonversionError) {
                showLoading(false)
                showError(requireContext(), error, TAG)
            }
        }
    }

    private fun goToAuth() {
        val intent = AuthActivity.getCallingIntent(requireContext())
        startActivity(intent)
    }

    private fun updateContent(products: Map<String, QProduct>) {
        binding.buttonRestore.text = getStr(R.string.restore_purchases)

        val subscription = products[subscriptionProductId]
        if (subscription != null) {
            binding.buttonSubscribe.text = String.format(
                Locale.getDefault(),
                "%s %s / %d %s",
                getStr(R.string.subscribe_for),
                subscription.prettyPrice,
                subscription.subscriptionPeriod?.unitCount,
                subscription.subscriptionPeriod?.unit?.name,
            )
        }

        val inApp = products[inAppProductId]
        if (inApp != null) {
            binding.buttonInApp.text = String.format(
                "%s %s", getStr(R.string.buy_for),
                inApp.prettyPrice
            )
        }
    }

    private fun handleEntitlements(entitlements: Map<String, QEntitlement>) {
        var isNothingToRestore = true
        val entitlementForSub = entitlements[entitlementIdForSubscription]
        if (entitlementForSub != null && entitlementForSub.isActive) {
            binding.buttonSubscribe.toSuccessState()
            isNothingToRestore = false
        }
        val entitlementForInApp = entitlements[entitlementIdForInApp]
        if (entitlementForInApp != null && entitlementForInApp.isActive) {
            binding.buttonInApp.toSuccessState()
            isNothingToRestore = false
        }

        if (isNothingToRestore) {
            binding.buttonRestore.text = getStr(R.string.nothing_to_restore)
        }
    }

    private fun purchase(productId: String) {
        Qonversion.shared.products(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) {
                val product = products[productId] ?: let {
                    Toast.makeText(requireContext(), "Product $productId not found", Toast.LENGTH_LONG).show()
                    return
                }
                purchase(product)
            }

            override fun onError(error: QonversionError) {
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun purchase(product: QProduct) {
        Qonversion.shared.purchase(
            requireActivity(),
            product,
            object : QonversionEntitlementsCallback {
                override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                    when (product.qonversionID) {
                        subscriptionProductId -> binding.buttonSubscribe.toSuccessState()
                        inAppProductId -> binding.buttonInApp.toSuccessState()
                    }
                }

                override fun onError(error: QonversionError) {
                    showError(requireContext(), error, TAG)
                }
            })
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) {
            ProgressBar.VISIBLE
        } else {
            ProgressBar.INVISIBLE
        }
    }

    private fun getStr(stringId: Int): String {
        return try {
            requireContext().getString(stringId)
        } catch (e: IllegalStateException) {
            val errorResult = "There is no context"
            Log.d(TAG, "$errorResult: ${e.localizedMessage}")
            errorResult
        }
    }

    private fun MaterialButton.toSuccessState() {
        val successColor = ContextCompat.getColor(context, R.color.colorGreen)
        val textColor = ContextCompat.getColor(context, R.color.colorWhite)
        text = getStr(R.string.successfully_purchased)
        setBackgroundColor(successColor)
        setStrokeColorResource(R.color.colorGreen)
        setTextColor(textColor)
    }
}
