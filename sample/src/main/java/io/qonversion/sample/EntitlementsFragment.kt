package io.qonversion.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QPurchaseResult
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import io.qonversion.sample.databinding.FragmentEntitlementsBinding

private const val TAG = "EntitlementsFragment"

class EntitlementsFragment : Fragment() {

    private var _binding: FragmentEntitlementsBinding? = null
    private val binding get() = _binding!!

    private var isListenerSet = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntitlementsBinding.inflate(inflater, container, false)

        binding.recyclerViewEntitlements.layoutManager = LinearLayoutManager(context)
        setupButtons()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupButtons() {
        binding.buttonLoadEntitlements.setOnClickListener {
            loadEntitlements()
        }

        binding.buttonSetListener.setOnClickListener {
            setEntitlementsListener()
        }

        binding.buttonRestore.setOnClickListener {
            restore()
        }

        binding.buttonSyncPurchases.setOnClickListener {
            syncPurchases()
        }
    }

    private fun loadEntitlements() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateContainer.visibility = View.GONE
        binding.recyclerViewEntitlements.visibility = View.GONE

        Qonversion.shared.checkEntitlements(object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                    displayEntitlements(entitlements)
                }
            }

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                    b.emptyStateContainer.visibility = View.VISIBLE
                    b.emptyStateText.text = getString(R.string.error_loading_entitlements)
                }
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun setEntitlementsListener() {
        Qonversion.shared.setEntitlementsUpdateListener(object : QEntitlementsUpdateListener {
            override fun onEntitlementsUpdated(
                entitlements: Map<String, QEntitlement>,
                purchaseResult: QPurchaseResult?
            ) {
                _binding?.let {
                    displayEntitlements(entitlements)
                }

                if (purchaseResult?.isSuccessful == true && entitlements.isEmpty()) {
                    Toast.makeText(
                        context,
                        "Consumable purchase completed in background",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(context, getString(R.string.entitlements_updated_via_listener), Toast.LENGTH_SHORT).show()
                }
            }
        })
        isListenerSet = true
        binding.buttonSetListener.text = getString(R.string.listener_set)
        binding.buttonSetListener.isEnabled = false
        Toast.makeText(context, getString(R.string.entitlements_listener_set), Toast.LENGTH_SHORT).show()
    }

    private fun restore() {
        binding.progressBar.visibility = View.VISIBLE

        Qonversion.shared.restore(object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                    displayEntitlements(entitlements)
                    Toast.makeText(context, getString(R.string.purchases_restored), Toast.LENGTH_SHORT).show()
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

    private fun syncPurchases() {
        Qonversion.shared.syncPurchases()
        Toast.makeText(context, getString(R.string.purchases_synced), Toast.LENGTH_SHORT).show()
    }

    private fun displayEntitlements(entitlements: Map<String, QEntitlement>) {
        if (entitlements.isEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.emptyStateText.text = getString(R.string.no_entitlements_found)
            binding.recyclerViewEntitlements.visibility = View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.recyclerViewEntitlements.visibility = View.VISIBLE
            binding.recyclerViewEntitlements.adapter = EntitlementsAdapter(entitlements.values.toList())
        }
    }
}
