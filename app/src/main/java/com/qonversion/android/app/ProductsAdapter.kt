package com.qonversion.android.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.sdk.dto.products.QProduct
import kotlinx.android.synthetic.main.table_row_product.view.*

class ProductsAdapter(
    private val products: List<QProduct>,
    private val onItemClicked: (QProduct) -> Unit
) :
    RecyclerView.Adapter<ProductsAdapter.RowViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.table_row_product, parent, false)
        return RowViewHolder(itemView) { index ->
            onItemClicked(products[index])
        }
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) =
        holder.bind(products[position])

    override fun getItemCount() = products.size

    inner class RowViewHolder(itemView: View, onItemClicked: (Int) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                onItemClicked(adapterPosition)
            }
        }

        fun bind(product: QProduct) = with(itemView) {
            txtName.text = product.qonversionID
            txtDescription.text = product.skuDetail?.description
            txtPrice.text = product.skuDetail?.price
        }
    }
}