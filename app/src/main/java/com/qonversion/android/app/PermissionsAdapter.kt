package com.qonversion.android.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.sdk.old.dto.QPermission
import kotlinx.android.synthetic.main.table_row_permission.view.*

class PermissionsAdapter(private val permissions: List<QPermission>) :
    RecyclerView.Adapter<PermissionsAdapter.RowViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.table_row_permission, parent, false)
        return RowViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) =
        holder.bind(permissions[position])

    override fun getItemCount() = permissions.size

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(permission: QPermission) = with(itemView) {
            txtPermissionId.text = permission.permissionID
            txtProductId.text = permission.productID
            txtRenewStateLabel.text = permission.renewState.name
        }
    }
}