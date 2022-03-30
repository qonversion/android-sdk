package com.qonversion.android.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.app.databinding.TableRowPermissionBinding
import com.qonversion.android.sdk.old.dto.QPermission

class PermissionsAdapter(private val permissions: List<QPermission>) :
    RecyclerView.Adapter<PermissionsAdapter.RowViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemViewBinding = TableRowPermissionBinding.inflate(inflater)
        return RowViewHolder(itemViewBinding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) =
        holder.bind(permissions[position])

    override fun getItemCount() = permissions.size

    inner class RowViewHolder(
        private val binding: TableRowPermissionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(permission: QPermission) = with(binding.root) {
            binding.txtPermissionId.text = permission.permissionID
            binding.txtProductId.text = permission.productID
            binding.txtRenewStateLabel.text = permission.renewState.name
        }
    }
}