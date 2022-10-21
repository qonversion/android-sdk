package com.qonversion.android.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.qonversion.android.app.databinding.FragmentOfferingsBinding
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionPermissionsCallback
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct

class OfferingsFragment : Fragment() {
    private val TAG = "OfferingsFragment"
    lateinit var binding: FragmentOfferingsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentOfferingsBinding.inflate(inflater)

        binding.recyclerViewProductsList.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewProductsList.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )

        Qonversion.offerings(object : QonversionOfferingsCallback {
            override fun onSuccess(offerings: QOfferings) {
                val mainProducts = offerings.main?.products
                mainProducts?.let {
                    binding.recyclerViewProductsList.adapter = ProductsAdapter(it) { product ->
                        purchase(product)
                    }
                } ?:  Toast.makeText(context, "There are no products in main offering", Toast.LENGTH_LONG).show()
            }

            override fun onError(error: QonversionError) {
                Toast.makeText(context, error.description, Toast.LENGTH_LONG).show()
                Log.e(TAG, error.toString())
            }
        })

        return binding.root
    }

    private fun purchase(product: QProduct) {
        Qonversion.purchase(requireActivity(), product, callback = object :
            QonversionPermissionsCallback {
            override fun onSuccess(permissions: Map<String, QPermission>) {
                Toast.makeText(context, "Purchase succeeded", Toast.LENGTH_LONG).show()
            }

            override fun onError(error: QonversionError) {
                showError(requireContext(), error, TAG)
            }
        })
    }
}