package com.qonversion.android.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.qonversion.android.app.databinding.FragmentHomeBinding
import com.qonversion.android.sdk.*
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import java.util.*

private const val TAG = "HomeFragment"

class HomeFragment : Fragment() {
    lateinit var binding: FragmentHomeBinding

    private val productIdSubs = "weekly"
    private val productIdInApp = "in_app"
    private val entitlementPlus = "plus"
    private val entitlementStandart = "standart"
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
                Locale.getDefault(),
                "%s %s / %d %s",
                getStr(R.string.subscribe_for),
                subscription.prettyPrice,
                subscription.subscriptionPeriod?.unitCount,
                subscription.subscriptionPeriod?.unit?.name,
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
        Qonversion.shared.products(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) {
                val product = products[productId] ?: let {
                    Toast.makeText(requireContext(), "Product $productId not found", Toast.LENGTH_LONG).show()
                    return;
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
            callback = object : QonversionEntitlementsCallback {
                override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                    when (product.qonversionID) {
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

    private fun MaterialButton.toSuccessState() {
        val successColor = ContextCompat.getColor(context, R.color.colorGreen)
        val textColor = ContextCompat.getColor(context, R.color.colorWhite)
        text = getStr(R.string.successfully_purchased)
        setBackgroundColor(successColor)
        setStrokeColorResource(R.color.colorGreen)
        setTextColor(textColor)
    }
}
