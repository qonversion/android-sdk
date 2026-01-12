package io.qonversion.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QPurchaseResult
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionPurchaseCallback
import io.qonversion.sample.databinding.FragmentOfferingsBinding

private const val TAG = "OfferingsFragment"

class OfferingsFragment : Fragment() {

    private var _binding: FragmentOfferingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOfferingsBinding.inflate(inflater, container, false)

        binding.recyclerViewOfferings.layoutManager = LinearLayoutManager(context)

        binding.buttonLoadOfferings.setOnClickListener {
            loadOfferings()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadOfferings() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateContainer.visibility = View.GONE
        binding.recyclerViewOfferings.visibility = View.GONE

        Qonversion.shared.offerings(object : QonversionOfferingsCallback {
            override fun onSuccess(offerings: QOfferings) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE

                    val allOfferings = offerings.availableOfferings
                    if (allOfferings.isEmpty()) {
                        b.emptyStateContainer.visibility = View.VISIBLE
                        b.emptyStateText.text = getString(R.string.no_offerings_found)
                    } else {
                        b.recyclerViewOfferings.visibility = View.VISIBLE
                        b.mainOfferingId.text = getString(R.string.main_offering_id, offerings.main?.offeringId ?: "N/A")
                        b.mainOfferingId.visibility = View.VISIBLE
                        b.recyclerViewOfferings.adapter = OfferingsAdapter(allOfferings) { product ->
                            purchase(product)
                        }
                    }
                }
            }

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                    b.emptyStateContainer.visibility = View.VISIBLE
                    b.emptyStateText.text = getString(R.string.error_loading_offerings)
                }
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun purchase(product: QProduct) {
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
                                val message = if (result.isFallbackGenerated) {
                                    getString(R.string.purchase_succeeded_with_fallback)
                                } else {
                                    getString(R.string.purchase_succeeded)
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                            result.isCanceledByUser -> {
                                Toast.makeText(context, getString(R.string.purchase_canceled), Toast.LENGTH_LONG).show()
                            }
                            result.isPending -> {
                                Toast.makeText(context, getString(R.string.purchase_pending), Toast.LENGTH_LONG).show()
                            }
                            result.isError -> {
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
