package com.qonversion.android.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.sdk.dto.QEntitlement
import kotlinx.android.synthetic.main.table_row_permission.view.*

class PermissionsAdapter(private val entitlements: List<QEntitlement>) :
    RecyclerView.Adapter<PermissionsAdapter.RowViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.table_row_permission, parent, false)
        return RowViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) =
        holder.bind(entitlements[position])

    override fun getItemCount() = entitlements.size

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(entitlement: QEntitlement) = with(itemView) {
            txtPermissionId.text = entitlement.permissionID
            txtProductId.text = entitlement.product.productID
            txtRenewStateLabel.text = entitlement.product.subscription?.renewState?.name
        }
    }
}
