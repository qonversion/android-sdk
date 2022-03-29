package com.qonversion.android.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.app.databinding.TableRowProductBinding
import com.qonversion.android.sdk.old.dto.products.QProduct

class ProductsAdapter(
    private val products: List<QProduct>,
    private val onItemClicked: (QProduct) -> Unit
) :
    RecyclerView.Adapter<ProductsAdapter.RowViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = TableRowProductBinding.inflate(inflater)
        return RowViewHolder(binding) { index ->
            onItemClicked(products[index])
        }
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) =
        holder.bind(products[position])

    override fun getItemCount() = products.size

    inner class RowViewHolder(
        private val binding: TableRowProductBinding,
        onItemClicked: (Int) -> Unit
    ) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                onItemClicked(adapterPosition)
            }
        }

        fun bind(product: QProduct) = with(binding.root) {
            binding.txtName.text = product.qonversionID
            binding.txtDescription.text = product.skuDetail?.description
            binding.txtPrice.text = product.skuDetail?.price
        }
    }
}