package io.qonversion.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QPurchaseResult
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.listeners.QonversionPurchaseCallback
import io.qonversion.sample.databinding.FragmentProductDetailBinding

private const val TAG = "ProductDetailFragment"

class ProductDetailFragment : Fragment() {

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!
    private var currentProduct: QProduct? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductDetailBinding.inflate(inflater, container, false)

        val productId = arguments?.getString("productId")
        if (productId != null) {
            loadProduct(productId)
        }

        binding.buttonPurchase.setOnClickListener {
            currentProduct?.let { purchase(it) }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadProduct(productId: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.contentContainer.visibility = View.GONE

        Qonversion.shared.products(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                    val product = products[productId]
                    if (product != null) {
                        currentProduct = product
                        displayProduct(product)
                        b.contentContainer.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(context, getString(R.string.product_not_found), Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                }
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun displayProduct(product: QProduct) {
        binding.productId.text = product.qonversionId
        binding.storeId.text = product.storeId ?: getString(R.string.not_available)
        binding.basePlanId.text = product.basePlanId ?: getString(R.string.not_available)
        binding.offerId.text = product.storeDetails?.defaultSubscriptionOfferDetails?.offerId ?: getString(R.string.not_available)
        binding.offeringId.text = product.offeringId ?: getString(R.string.not_available)
        binding.productType.text = product.type.name
        
        val storeDetails = product.storeDetails
        if (storeDetails != null) {
            binding.storeTitle.text = storeDetails.title
            binding.storeDescription.text = storeDetails.description
            binding.price.text = product.prettyPrice ?: getString(R.string.not_available)
            
            val period = product.subscriptionPeriod
            if (period != null) {
                binding.subscriptionPeriod.text = "${period.unitCount} ${period.unit.name}"
            } else {
                binding.subscriptionPeriod.text = getString(R.string.not_applicable)
            }
            
            val trialPeriod = product.trialPeriod
            if (trialPeriod != null) {
                binding.trialPeriod.text = "${trialPeriod.unitCount} ${trialPeriod.unit.name}"
            } else {
                binding.trialPeriod.text = getString(R.string.not_available)
            }
        } else {
            binding.storeTitle.text = getString(R.string.not_available)
            binding.storeDescription.text = getString(R.string.not_available)
            binding.price.text = getString(R.string.not_available)
            binding.subscriptionPeriod.text = getString(R.string.not_available)
            binding.trialPeriod.text = getString(R.string.not_available)
        }

        binding.buttonPurchase.text = getString(R.string.purchase_for, product.prettyPrice ?: "N/A")
    }

    private fun purchase(product: QProduct) {
        binding.buttonPurchase.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        Qonversion.shared.purchase(
            requireActivity(),
            product,
            object : QonversionPurchaseCallback {
                override fun onResult(result: QPurchaseResult) {
                    _binding?.let { b ->
                        b.progressBar.visibility = View.GONE
                        when {
                            result.isSuccessful -> {
                                b.buttonPurchase.text = getString(R.string.successfully_purchased)
                                b.buttonPurchase.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorGreen))
                                val message = if (result.isFallbackGenerated) {
                                    getString(R.string.purchase_succeeded_with_fallback)
                                } else {
                                    getString(R.string.purchase_succeeded)
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                            result.isCanceledByUser -> {
                                b.buttonPurchase.isEnabled = true
                                Toast.makeText(context, getString(R.string.purchase_canceled), Toast.LENGTH_LONG).show()
                            }
                            result.isPending -> {
                                b.buttonPurchase.isEnabled = true
                                Toast.makeText(context, getString(R.string.purchase_pending), Toast.LENGTH_LONG).show()
                            }
                            result.isError -> {
                                b.buttonPurchase.isEnabled = true
                                val error = result.error
                                if (error != null) {
                                    showError(requireContext(), error, TAG)
                                } else {
                                    Toast.makeText(requireContext(), getString(R.string.purchase_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}
