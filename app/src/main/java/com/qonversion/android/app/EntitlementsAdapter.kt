package com.qonversion.android.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.sdk.dto.QEntitlement
import kotlinx.android.synthetic.main.table_row_entitlement.view.*

class EntitlementsAdapter(private val entitlements: List<QEntitlement>) :
    RecyclerView.Adapter<EntitlementsAdapter.RowViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.table_row_entitlement, parent, false)
        return RowViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) =
        holder.bind(entitlements[position])

    override fun getItemCount() = entitlements.size

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(entitlement: QEntitlement) = with(itemView) {
            txtEntitlementId.text = entitlement.id
            txtProductId.text = entitlement.product.productId
            txtRenewStateLabel.text = entitlement.product.subscription?.renewState?.name
        }
    }
}
