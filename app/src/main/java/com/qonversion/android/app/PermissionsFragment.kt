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
import com.qonversion.android.app.databinding.FragmentPermissionsBinding
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback

class PermissionsFragment : Fragment() {
    private val TAG = "PermissionsFragment"
    lateinit var binding: FragmentPermissionsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPermissionsBinding.inflate(inflater)

        binding.recyclerViewPermissionsList.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewPermissionsList.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )

        Qonversion.sharedInstance.checkEntitlements(object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                val activePermissions = entitlements.values.filter { it.isActive }
                binding.recyclerViewPermissionsList.adapter = PermissionsAdapter(activePermissions)
            }

            override fun onError(error: QonversionError) {
                Toast.makeText(context, error.description, Toast.LENGTH_LONG).show()
                Log.e(TAG, error.toString())
            }
        })

        return binding.root
    }
}