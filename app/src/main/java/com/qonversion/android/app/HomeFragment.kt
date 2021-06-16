package com.qonversion.android.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.app.databinding.FragmentHomeBinding
import com.qonversion.android.sdk.*
import com.qonversion.android.sdk.automations.Automations
import com.qonversion.android.sdk.automations.AutomationsDelegate
import com.qonversion.android.sdk.automations.QActionResult
import com.qonversion.android.sdk.automations.QActionResultType
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.products.QProduct

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
                showLoading(false)
                updateContent(products)
            }

            override fun onError(error: QonversionError) {
                showLoading(false)
                showError(error)
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
            Qonversion.restore(object : QonversionPermissionsCallback {
                override fun onSuccess(permissions: Map<String, QPermission>) {
                    showLoading(false)
                    handleRestore(permissions)
                }

                override fun onError(error: QonversionError) {
                    showLoading(false)
                    showError(error)
                }
            })
        }

        binding.buttonPermissions.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeFragmentToPermissionsFragment()
            findNavController().navigate(action)
        }

        // Automation
        // You can skip this step if you don't need to handle the Qonversion Automations result
        Automations.setDelegate(automationsDelegate)

        // Check if the activity was launched from a push notification
        val remoteMessage: RemoteMessage? = requireActivity().intent.getParcelableExtra(FirebaseMessageReceiver.INTENT_REMOTE_MESSAGE)
        if (remoteMessage != null && !Qonversion.handleNotification(remoteMessage)) {
            // Handle notification yourself
        }


        return binding.root
    }

    private fun updateContent(products: Map<String, QProduct>) {
        binding.buttonPermissions.text = getString(R.string.check_active_permissions)
        binding.buttonRestore.text = getString(R.string.restore_purchases)

        val subscription = products[productIdSubs]
        if (subscription != null) {
            binding.buttonSubscribe.text = String.format(
                "%s %s / %s", getString(R.string.subscribe_for),
                subscription.prettyPrice, subscription.duration?.name
            )
        }

        val inApp = products[productIdInApp]
        if (inApp != null) {
            binding.buttonInApp.text = String.format(
                "%s %s", getString(R.string.buy_for),
                inApp.prettyPrice
            )
        }
    }

    private fun handleRestore(permissions: Map<String, QPermission>) {
        var isNothingToRestore = true
        val permissionPlus = permissions[permissionPlus]
        if (permissionPlus != null && permissionPlus.isActive()) {
            binding.buttonSubscribe.text = getString(R.string.purchased)
            isNothingToRestore = false
        }
        val permissionStandart = permissions[permissionStandart]
        if (permissionStandart != null && permissionStandart.isActive()) {
            binding.buttonInApp.text = getString(R.string.purchased)
            isNothingToRestore = false
        }

        if (isNothingToRestore) {
            binding.buttonRestore.text = getString(R.string.nothing_to_restore)
        }
    }

    private fun purchase(productId: String) {
        Qonversion.purchase(
            requireActivity(),
            productId,
            callback = object : QonversionPermissionsCallback {
                override fun onSuccess(permissions: Map<String, QPermission>) {
                    when (productId) {
                        productIdInApp -> binding.buttonInApp.text = getString(R.string.purchased)
                        productIdSubs -> binding.buttonSubscribe.text =
                            getString(R.string.purchased)
                    }
                }

                override fun onError(error: QonversionError) {
                    showError(error)
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

    private fun showError(error: QonversionError) {
        val code = error.code                           // Error enum code
        val description = error.description             // Error enum code description
        val additionalMessage =
            error.additionalMessage // Additional error information (if possible)
        Toast.makeText(context, error.description, Toast.LENGTH_LONG).show()
        Log.e(
            TAG,
            "error code: $code, description: $description, additionalMessage: $additionalMessage"
        )
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

}