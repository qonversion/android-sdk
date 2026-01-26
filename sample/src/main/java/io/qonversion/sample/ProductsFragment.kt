package io.qonversion.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.qonversion.android.sdk.dto.products.QProduct
import io.qonversion.sample.databinding.FragmentProductsBinding

private const val TAG = "ProductsFragment"

class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProductsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)

        binding.recyclerViewProducts.layoutManager = LinearLayoutManager(context)

        binding.buttonLoadProducts.setOnClickListener {
            viewModel.loadProducts(forceRefresh = true)
        }

        observeViewModel()

        // Show empty state initially if no data
        if (viewModel.products.value.isNullOrEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                binding.emptyStateContainer.visibility = View.GONE
                binding.recyclerViewProducts.visibility = View.GONE
            }
        }

        viewModel.products.observe(viewLifecycleOwner) { products ->
            if (products.isEmpty()) {
                binding.emptyStateContainer.visibility = View.VISIBLE
                binding.emptyStateText.text = getString(R.string.no_products_found)
                binding.recyclerViewProducts.visibility = View.GONE
            } else {
                binding.emptyStateContainer.visibility = View.GONE
                binding.recyclerViewProducts.visibility = View.VISIBLE
                binding.recyclerViewProducts.adapter = ProductsAdapter(products) { product ->
                    navigateToProductDetail(product)
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                binding.emptyStateContainer.visibility = View.VISIBLE
                binding.emptyStateText.text = getString(R.string.error_loading_products)
                showError(requireContext(), it, TAG)
                viewModel.clearError()
            }
        }
    }

    private fun navigateToProductDetail(product: QProduct) {
        val bundle = Bundle().apply {
            putString("productId", product.qonversionId)
        }
        findNavController().navigate(R.id.action_productsFragment_to_productDetailFragment, bundle)
    }
}
