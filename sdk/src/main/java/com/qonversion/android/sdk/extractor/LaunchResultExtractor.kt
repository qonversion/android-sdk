package com.qonversion.android.sdk.extractor

import com.qonversion.android.sdk.dto.*
import java.util.*

class LaunchResultExtractor: NewExtractor<QLaunchResult?> {
    override fun extract(response: retrofit2.Response<BaseResponse<Response>>): QLaunchResult? {
        val product1 = mapOf(
            "duration" to 0,
            "id" to "main",
            "store_id" to "com.subs.weekly",
            "type" to 1
        )

        val product2 = mapOf(
            "duration" to null,
            "id" to "in_app",
            "store_id" to "com.subs.nonconsumable",
            "type" to 2
        )

        val product3 = mapOf(
            "duration" to 4,
            "id" to "annual",
            "store_id" to "com.subs.annual",
            "type" to 1
        )

        val mockData = mapOf(
            "timestamp" to 1601480766,
            "uid" to "3Dr_6gl0a1SwKegmtqtNpA0ze7juctzX",
            "user_products" to listOf<Any>(),
            "permissions" to listOf<Any>(),
            "products" to listOf(product1, product2, product3)
        )
        val mockResponse = BaseResponse(success = true, data = mockData)

        val result: QLaunchResult? = mockResponse.let {
//            val uid = it.data.getValue("uid") as? String
//            if (uid.isNullOrEmpty()) {
//                return null
//            }
//
//            val permissionsData = it.data.getValue("permissions") as? List<Map<String, Any>>
//            var mappedPermissions: Map<String, QPermission>? = null
//            if (!permissionsData.isNullOrEmpty()) {
//                mappedPermissions = mapPermissions(permissionsData)
//            }
//
//            val productsData = it.data.getValue("products") as? List<Map<String, Any>>
//            var mappedProducts: Map<String, QProduct>? = null
//            if (!productsData.isNullOrEmpty()) {
//                mappedProducts = mapProducts(productsData)
//            }
//
//            val userProductsData = it.data.getValue("user_products") as? List<Map<String, Any>>
//            var mappedUserProducts: Map<String, QProduct>? = null
//            if (!userProductsData.isNullOrEmpty()) {
//                mappedUserProducts = mapProducts(userProductsData)
//            }
//
//            QLaunchResult(
//                uid,
//                it.data.getValue("timestamp") as Int,
//                mappedPermissions,
//                mappedProducts,
//                mappedUserProducts
//            )
            return null
        }

        return result
    }

//    fun mapProducts(productsData: List<Map<String, Any>>): Map<String, QProduct> {
//        val products: Map<String, QProduct> = productsData.let { products ->
//            val resultProducts = mutableMapOf<String, QProduct>()
//            products?.forEach { product ->
//                val id = product.getValue("id") as? String
//                if (id.isNullOrEmpty()) {
//                    return@forEach
//                }
//
//                val mappedProduct = QProduct(
//                    id,
//                    product.getValue("store_id") as? String,
//                    product.getValue("type").toString()
//                )
//                resultProducts[product.getValue("id") as String] = mappedProduct
//            }
//
//            resultProducts
//        }
//
//        return products
//    }
//
//    fun mapPermissions(permissionsData: List<Map<String, Any>>): Map<String, QPermission> {
//        val permissions: Map<String, QPermission> = permissionsData.let { permissions ->
//            val resultPermissions = mutableMapOf<String, QPermission>()
//            permissions?.forEach { permission ->
//                val id = permission.getValue("id") as? String
//                if (id.isNullOrEmpty()) {
//                    return@forEach
//                }
//
//                val mappedPermission = QPermission(
//                    id,
//                    permission.getValue("store_id") as String,
//                    false,
//                    permission.getValue("type").toString(),
//                    Calendar.getInstance().time,
//                    Calendar.getInstance().time
//                )
//                resultPermissions[permission.getValue("id") as String] = mappedPermission
//            }
//
//            resultPermissions
//        }
//
//        return permissions
//    }
}