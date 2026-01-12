package io.qonversion.sample

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.sdk.dto.products.QProduct
import io.qonversion.sample.databinding.ItemProductBinding

class ProductsAdapter(
    private val products: List<QProduct>,
    private val onProductClick: (QProduct) -> Unit
) : RecyclerView.Adapter<ProductsAdapter.ProductViewHolder>() {

    class ProductViewHolder(val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        with(holder.binding) {
            productId.text = product.qonversionId
            storeId.text = product.storeId ?: "N/A"
            productType.text = product.type.name
            price.text = product.prettyPrice ?: "N/A"
            
            val period = product.subscriptionPeriod
            subscriptionPeriod.text = if (period != null) {
                "${period.unitCount} ${period.unit.name}"
            } else {
                "N/A"
            }

            root.setOnClickListener {
                onProductClick(product)
            }
        }
    }

    override fun getItemCount() = products.size
}
