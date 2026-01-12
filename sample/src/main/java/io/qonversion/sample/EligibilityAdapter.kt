package io.qonversion.sample

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.eligibility.QIntroEligibilityStatus
import io.qonversion.sample.databinding.ItemEligibilityBinding

class EligibilityAdapter(
    private val eligibilities: Map<String, QEligibility>
) : RecyclerView.Adapter<EligibilityAdapter.EligibilityViewHolder>() {

    private val items = eligibilities.entries.toList()

    class EligibilityViewHolder(val binding: ItemEligibilityBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EligibilityViewHolder {
        val binding = ItemEligibilityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EligibilityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EligibilityViewHolder, position: Int) {
        val item = items[position]
        val productId = item.key
        val eligibility = item.value
        val context = holder.itemView.context

        with(holder.binding) {
            this.productId.text = productId
            status.text = eligibility.status.name

            val color = when (eligibility.status) {
                QIntroEligibilityStatus.Eligible -> R.color.colorGreen
                QIntroEligibilityStatus.Ineligible -> R.color.colorRed
                QIntroEligibilityStatus.Unknown -> R.color.colorGray
                QIntroEligibilityStatus.NonIntroOrTrialProduct -> R.color.colorGray
            }
            statusIndicator.setBackgroundColor(ContextCompat.getColor(context, color))
        }
    }

    override fun getItemCount() = items.size
}
