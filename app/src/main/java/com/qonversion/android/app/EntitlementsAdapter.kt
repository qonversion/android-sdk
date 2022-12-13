package com.qonversion.android.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.app.databinding.TableRowEntitlementBinding

class EntitlementsAdapter(private val entitlements: List<QEntitlement>) :
    RecyclerView.Adapter<EntitlementsAdapter.RowViewHolder>() {

    private lateinit var binding: TableRowEntitlementBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        binding = TableRowEntitlementBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RowViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) =
        holder.bind(entitlements[position])

    override fun getItemCount() = entitlements.size

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(entitlement: QEntitlement) = with(itemView) {
            binding.txtEntitlementId.text = entitlement.id
            binding.txtProductId.text = entitlement.productId
            binding.txtRenewStateLabel.text = entitlement.renewState.name
        }
    }
}
