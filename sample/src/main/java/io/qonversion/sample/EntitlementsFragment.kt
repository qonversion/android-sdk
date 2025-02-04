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
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import io.qonversion.sample.databinding.FragmentEntitlementsBinding


private const val TAG = "EntitlementsFragment"

class EntitlementsFragment : Fragment() {
    lateinit var binding: FragmentEntitlementsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEntitlementsBinding.inflate(inflater)

        binding.recyclerViewEntitlementsList.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewEntitlementsList.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )

        Qonversion.shared.checkEntitlements(object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                val activeEntitlements = entitlements.values.filter { it.isActive }
                binding.recyclerViewEntitlementsList.adapter = EntitlementsAdapter(activeEntitlements)
            }

            override fun onError(error: QonversionError) {
                Toast.makeText(context, error.description, Toast.LENGTH_LONG).show()
                Log.e(TAG, error.toString())
            }
        })

        return binding.root
    }
}