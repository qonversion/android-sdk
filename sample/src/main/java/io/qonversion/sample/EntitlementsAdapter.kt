package io.qonversion.sample

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import io.qonversion.sample.databinding.ItemEntitlementBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EntitlementsAdapter(
    private val entitlements: List<QEntitlement>
) : RecyclerView.Adapter<EntitlementsAdapter.EntitlementViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    class EntitlementViewHolder(val binding: ItemEntitlementBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntitlementViewHolder {
        val binding = ItemEntitlementBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EntitlementViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EntitlementViewHolder, position: Int) {
        val entitlement = entitlements[position]
        val context = holder.itemView.context

        with(holder.binding) {
            entitlementId.text = entitlement.id
            productId.text = entitlement.productId

            if (entitlement.isActive) {
                statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.colorGreen))
                statusText.text = context.getString(R.string.active)
                statusText.setTextColor(ContextCompat.getColor(context, R.color.colorGreen))
            } else {
                statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.colorRed))
                statusText.text = context.getString(R.string.inactive)
                statusText.setTextColor(ContextCompat.getColor(context, R.color.colorRed))
            }

            renewState.text = entitlement.renewState.name
            source.text = entitlement.source.name

            startedDate.text = formatDate(entitlement.startedDate)
            expirationDate.text = entitlement.expirationDate?.let { formatDate(it) }
                ?: context.getString(R.string.not_available)
        }
    }

    private fun formatDate(date: Date): String {
        return dateFormat.format(date)
    }

    override fun getItemCount() = entitlements.size
}
