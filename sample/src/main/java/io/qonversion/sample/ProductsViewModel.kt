package io.qonversion.sample

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionProductsCallback

class ProductsViewModel : ViewModel() {

    private val _products = MutableLiveData<List<QProduct>>()
    val products: LiveData<List<QProduct>> = _products

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<QonversionError?>()
    val error: LiveData<QonversionError?> = _error

    private var hasLoaded = false

    fun loadProducts(forceRefresh: Boolean = false) {
        if (hasLoaded && !forceRefresh && _products.value?.isNotEmpty() == true) {
            return
        }

        _isLoading.value = true
        _error.value = null

        Qonversion.shared.products(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) {
                _isLoading.postValue(false)
                _products.postValue(products.values.toList())
                hasLoaded = true
            }

            override fun onError(error: QonversionError) {
                _isLoading.postValue(false)
                _error.postValue(error)
            }
        })
    }

    fun clearError() {
        _error.value = null
    }
}
