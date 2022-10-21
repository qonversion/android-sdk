package com.qonversion.android.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.app.databinding.FragmentHomeBinding
import com.qonversion.android.sdk.*
import com.qonversion.android.sdk.automations.Automations
import com.qonversion.android.sdk.automations.AutomationsDelegate
import com.qonversion.android.sdk.automations.QActionResult
import com.qonversion.android.sdk.automations.QActionResultType
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionPermissionsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.listeners.UpdatedPurchasesListener

class HomeFragment : Fragment() {
    lateinit var binding: FragmentHomeBinding

    private val productIdSubs = "main"
    private val productIdInApp = "in_app"
    private val permissionPlus = "plus"
    private val permissionStandart = "standart"
    private val TAG = "HomeFragment"
    private val automationsDelegate = getAutomationsDelegate()
    private val purchasesListener = getUpdatedPurchasesListener()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater)

        // Product Center
        Qonversion.setUpdatedPurchasesListener(purchasesListener)

        Qonversion.products(callback = object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) {
                updateContent(products)
                Qonversion.checkPermissions(getPermissionsCallback())
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
            Qonversion.restore(getPermissionsCallback())
        }

        binding.buttonLogout.setOnClickListener {
            Firebase.auth.signOut()
            Qonversion.logout()

            goToAuth()
        }

        // Automation
        // You can skip this step if you don't need to handle the Qonversion Automations result
        Automations.setDelegate(automationsDelegate)

        // Check if the activity was launched from a push notification
        val remoteMessage: RemoteMessage? =
            requireActivity().intent.getParcelableExtra(FirebaseMessageReceiver.INTENT_REMOTE_MESSAGE)
        if (remoteMessage != null && !Qonversion.handleNotification(remoteMessage.data)) {
            // Handle notification yourself
        }

        return binding.root
    }

    private fun getPermissionsCallback(): QonversionPermissionsCallback {
        return object : QonversionPermissionsCallback {
            override fun onSuccess(permissions: Map<String, QPermission>) {
                showLoading(false)
                handlePermissions(permissions)
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

    private fun handlePermissions(permissions: Map<String, QPermission>) {
        var isNothingToRestore = true
        val permissionPlus = permissions[permissionPlus]
        if (permissionPlus != null && permissionPlus.isActive()) {
            binding.buttonSubscribe.toSuccessState()
            isNothingToRestore = false
        }
        val permissionStandart = permissions[permissionStandart]
        if (permissionStandart != null && permissionStandart.isActive()) {
            binding.buttonInApp.toSuccessState()
            isNothingToRestore = false
        }

        if (isNothingToRestore) {
            binding.buttonRestore.text = getStr(R.string.nothing_to_restore)
        }
    }

    private fun purchase(productId: String) {
        Qonversion.purchase(
            requireActivity(),
            productId,
            callback = object : QonversionPermissionsCallback {
                override fun onSuccess(permissions: Map<String, QPermission>) {
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

    private fun getUpdatedPurchasesListener() = object : UpdatedPurchasesListener {
        override fun onPermissionsUpdate(permissions: Map<String, QPermission>) {
            // handle updated permissions here
        }
    }

    private fun getAutomationsDelegate() = object : AutomationsDelegate {
        override fun automationsDidFinishExecuting(actionResult: QActionResult) {
            // Handle the final action that the user completed on the in-app screen.
            if (actionResult.type == QActionResultType.Purchase) {
                // You can check available permissions
                Qonversion.checkPermissions(object : QonversionPermissionsCallback {
                    override fun onSuccess(permissions: Map<String, QPermission>) {
                        // Handle new permissions here
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
        val successColor = resources.getColor(R.color.colorGreen)
        val textColor = resources.getColor(R.color.colorWhite)
        text = getStr(R.string.successfully_purchased)
        setBackgroundColor(successColor)
        setStrokeColorResource(R.color.colorGreen)
        setTextColor(textColor)
    }
}