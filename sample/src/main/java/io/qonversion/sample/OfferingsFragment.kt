package io.qonversion.sample

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionPurchaseResultCallback
import com.qonversion.android.sdk.dto.QPurchaseResult
import io.qonversion.sample.databinding.FragmentOfferingsBinding

private const val TAG = "OfferingsFragment"

class OfferingsFragment : Fragment() {
    lateinit var binding: FragmentOfferingsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOfferingsBinding.inflate(inflater)

        binding.recyclerViewProductsList.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewProductsList.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )

        Qonversion.shared.offerings(object : QonversionOfferingsCallback {
            override fun onSuccess(offerings: QOfferings) {
                val mainProducts = offerings.main?.products
                mainProducts?.let {
                    binding.recyclerViewProductsList.adapter = ProductsAdapter(it) { product ->
                        purchase(product)
                    }
                } ?: Toast.makeText(context, "There in no product in the main offering", Toast.LENGTH_LONG).show()
            }

            override fun onError(error: QonversionError) {
                Toast.makeText(context, error.description, Toast.LENGTH_LONG).show()
                Log.e(TAG, error.toString())
            }
        })

        return binding.root
    }

    private fun purchase(product: QProduct) {
        Qonversion.shared.purchase(
            requireActivity(),
            product,
            object : QonversionPurchaseResultCallback {
                override fun onResult(result: QPurchaseResult) {
                    when {
                        result.isSuccessful -> {
                            val message = if (result.isFallbackGenerated) {
                                "Purchase succeeded with fallback"
                            } else {
                                "Purchase succeeded"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                        result.isCanceledByUser -> {
                            Toast.makeText(context, "Purchase canceled by user", Toast.LENGTH_LONG).show()
                        }
                        result.isPending -> {
                            Toast.makeText(context, "Purchase is pending", Toast.LENGTH_LONG).show()
                        }
                        result.isError -> {
                            val error = result.error
                            if (error != null) {
                                showError(requireContext(), error, TAG)
                            } else {
                                Toast.makeText(requireContext(), "Purchase failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            })
    }
}
