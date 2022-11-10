package com.qonversion.android.app

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.app.databinding.FragmentHomeBinding
import com.qonversion.android.sdk.*
import com.qonversion.android.sdk.automations.Automations
import com.qonversion.android.sdk.automations.AutomationsDelegate
import com.qonversion.android.sdk.automations.dto.QActionResult
import com.qonversion.android.sdk.automations.dto.QActionResultType
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback

private const val TAG = "HomeFragment"

class HomeFragment : Fragment() {
    lateinit var binding: FragmentHomeBinding

    private val productIdSubs = "main"
    private val productIdInApp = "in_app"
    private val entitlementPlus = "plus"
    private val entitlementStandart = "standart"
    private val automationsDelegate = getAutomationsDelegate()
    private val entitlementsUpdateListener = getEntitlementsUpdateListener()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater)

        // Product Center
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

        binding.buttonSubscribe.setOnClickListener {
            purchase(productIdSubs)
        }

        binding.buttonInApp.setOnClickListener {
            purchase(productIdInApp)
        }

        binding.buttonRestore.setOnClickListener {
            showLoading(true)
            Qonversion.shared.restore(getEntitlementsCallback())
        }

        binding.buttonLogout.setOnClickListener {
            Firebase.auth.signOut()
            Qonversion.shared.logout()

            goToAuth()
        }

        // Automation
        // You can skip this step if you don't need to handle the Qonversion Automations result
        Automations.sharedInstance.setDelegate(automationsDelegate)

        // Check if the activity was launched from a push notification
        val remoteMessage: RemoteMessage? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireActivity().intent.getParcelableExtra(
                    FirebaseMessageReceiver.INTENT_REMOTE_MESSAGE,
                    RemoteMessage::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                requireActivity().intent.getParcelableExtra(
                    FirebaseMessageReceiver.INTENT_REMOTE_MESSAGE
                )
            }

        @Suppress("ControlFlowWithEmptyBody")
        if (remoteMessage != null && !Automations.sharedInstance.handleNotification(remoteMessage.data)) {
            // Handle notification yourself
        }

        return binding.root
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

        val subscription = products[productIdSubs]
        if (subscription != null) {
            binding.buttonSubscribe.text = String.format(
                "%s %s / %s", getStr(R.string.subscribe_for),
                subscription.prettyPrice, subscription.duration?.name
            )
        }

        val inApp = products[productIdInApp]
        if (inApp != null) {
            binding.buttonInApp.text = String.format(
                "%s %s", getStr(R.string.buy_for),
                inApp.prettyPrice
            )
        }
    }

    private fun handleEntitlements(entitlements: Map<String, QEntitlement>) {
        var isNothingToRestore = true
        val entitlementPlus = entitlements[entitlementPlus]
        if (entitlementPlus != null && entitlementPlus.isActive) {
            binding.buttonSubscribe.toSuccessState()
            isNothingToRestore = false
        }
        val entitlementStandart = entitlements[entitlementStandart]
        if (entitlementStandart != null && entitlementStandart.isActive) {
            binding.buttonInApp.toSuccessState()
            isNothingToRestore = false
        }

        if (isNothingToRestore) {
            binding.buttonRestore.text = getStr(R.string.nothing_to_restore)
        }
    }

    private fun purchase(productId: String) {
        Qonversion.shared.purchase(
            requireActivity(),
            productId,
            callback = object : QonversionEntitlementsCallback {
                override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                    when (productId) {
                        productIdSubs -> binding.buttonSubscribe.toSuccessState()
                        productIdInApp -> binding.buttonInApp.toSuccessState()
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

    private fun getEntitlementsUpdateListener() = object : QEntitlementsUpdateListener {
        override fun onEntitlementsUpdated(entitlements: Map<String, QEntitlement>) {
            // handle updated entitlements here
        }
    }

    private fun getAutomationsDelegate() = object : AutomationsDelegate {
        override fun automationsDidFinishExecuting(actionResult: QActionResult) {
            // Handle the final action that the user completed on the in-app screen.
            if (actionResult.type == QActionResultType.Purchase) {
                // You can check available entitlements
                Qonversion.shared.checkEntitlements(object : QonversionEntitlementsCallback {
                    override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                        // Handle new entitlements here
                    }

                    override fun onError(error: QonversionError) {
                        // Handle the error
                    }
                })
            }
        }

        override fun automationsDidFailExecuting(actionResult: QActionResult) {
            // Do some logic or track event
        }


        override fun automationsDidStartExecuting(actionResult: QActionResult) {
            // Do some logic or track event
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
