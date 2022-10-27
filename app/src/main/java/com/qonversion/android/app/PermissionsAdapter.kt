package com.qonversion.android.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.app.databinding.TableRowPermissionBinding
import com.qonversion.android.sdk.dto.QPermission

class PermissionsAdapter(private val permissions: List<QPermission>) :
    RecyclerView.Adapter<PermissionsAdapter.RowViewHolder>() {

    private lateinit var binding: TableRowPermissionBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        binding = TableRowPermissionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RowViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) =
        holder.bind(permissions[position])

    override fun getItemCount() = permissions.size

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(permission: QPermission) = with(itemView) {
            binding.txtPermissionId.text = permission.permissionID
            binding.txtProductId.text = permission.productID
            binding.txtRenewStateLabel.text = permission.renewState.name
        }
    }
}