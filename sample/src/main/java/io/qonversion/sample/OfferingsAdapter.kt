package io.qonversion.sample

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.products.QProduct
import io.qonversion.sample.databinding.ItemOfferingBinding
import io.qonversion.sample.databinding.ItemOfferingProductBinding

class OfferingsAdapter(
    private val offerings: List<QOffering>,
    private val onProductClick: (QProduct) -> Unit
) : RecyclerView.Adapter<OfferingsAdapter.OfferingViewHolder>() {

    class OfferingViewHolder(val binding: ItemOfferingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OfferingViewHolder {
        val binding = ItemOfferingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OfferingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OfferingViewHolder, position: Int) {
        val offering = offerings[position]
        val context = holder.itemView.context

        with(holder.binding) {
            offeringId.text = offering.offeringId
            offeringTag.text = context.getString(R.string.tag_format, offering.tag.name)
            productsCount.text = context.getString(R.string.products_count, offering.products.size)

            recyclerViewProducts.layoutManager = LinearLayoutManager(context)
            recyclerViewProducts.adapter = OfferingProductsAdapter(offering.products, onProductClick)
        }
    }

    override fun getItemCount() = offerings.size
}

class OfferingProductsAdapter(
    private val products: List<QProduct>,
    private val onProductClick: (QProduct) -> Unit
) : RecyclerView.Adapter<OfferingProductsAdapter.ProductViewHolder>() {

    class ProductViewHolder(val binding: ItemOfferingProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemOfferingProductBinding.inflate(
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
            price.text = product.prettyPrice ?: "N/A"

            buttonPurchase.setOnClickListener {
                onProductClick(product)
            }
        }
    }

    override fun getItemCount() = products.size
}
