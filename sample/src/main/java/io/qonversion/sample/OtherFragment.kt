package io.qonversion.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import io.qonversion.sample.databinding.FragmentOtherBinding

private const val TAG = "OtherFragment"

class OtherFragment : Fragment() {

    private var _binding: FragmentOtherBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtherBinding.inflate(inflater, container, false)

        binding.recyclerViewEligibility.layoutManager = LinearLayoutManager(context)
        setupButtons()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupButtons() {
        // Navigation buttons
        binding.buttonOfferings.setOnClickListener {
            findNavController().navigate(R.id.offeringsFragment)
        }

        binding.buttonRemoteConfigs.setOnClickListener {
            findNavController().navigate(R.id.remoteConfigsFragment)
        }

        binding.buttonNoCodes.setOnClickListener {
            findNavController().navigate(R.id.noCodesFragment)
        }

        // Action buttons
        binding.buttonCheckFallback.setOnClickListener {
            checkFallbackFile()
        }

        binding.buttonCheckEligibility.setOnClickListener {
            checkTrialIntroEligibility()
        }

        binding.buttonSyncPurchases.setOnClickListener {
            syncPurchases()
        }
    }

    private fun checkFallbackFile() {
        binding.progressBar.visibility = View.VISIBLE
        try {
            val isAccessible = Qonversion.shared.isFallbackFileAccessible()
            binding.progressBar.visibility = View.GONE

            if (isAccessible) {
                binding.fallbackIndicator.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.colorGreen)
                )
                binding.fallbackStatusText.text = getString(R.string.fallback_accessible)
            } else {
                binding.fallbackIndicator.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.colorRed)
                )
                binding.fallbackStatusText.text = getString(R.string.fallback_not_accessible)
            }
            binding.fallbackContainer.visibility = View.VISIBLE
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(context, getString(R.string.error_checking_fallback), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkTrialIntroEligibility() {
        binding.progressBar.visibility = View.VISIBLE

        // First, get all products to check eligibility
        Qonversion.shared.products(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) {
                val productIds = products.keys.toList()
                if (productIds.isEmpty()) {
                    _binding?.let { b ->
                        b.progressBar.visibility = View.GONE
                        Toast.makeText(context, getString(R.string.no_products_to_check), Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                Qonversion.shared.checkTrialIntroEligibility(productIds, object : QonversionEligibilityCallback {
                    override fun onSuccess(eligibilities: Map<String, QEligibility>) {
                        _binding?.let { b ->
                            b.progressBar.visibility = View.GONE
                            displayEligibility(eligibilities)
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

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                }
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun displayEligibility(eligibilities: Map<String, QEligibility>) {
        if (eligibilities.isEmpty()) {
            binding.eligibilityEmptyText.visibility = View.VISIBLE
            binding.recyclerViewEligibility.visibility = View.GONE
        } else {
            binding.eligibilityEmptyText.visibility = View.GONE
            binding.recyclerViewEligibility.visibility = View.VISIBLE
            binding.recyclerViewEligibility.adapter = EligibilityAdapter(eligibilities)
        }
        binding.eligibilityContainer.visibility = View.VISIBLE
    }

    private fun syncPurchases() {
        Qonversion.shared.syncPurchases()
        Toast.makeText(context, getString(R.string.purchases_synced), Toast.LENGTH_SHORT).show()
    }
}
