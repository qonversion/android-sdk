package com.qonversion.android.sdk.automations.macros

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import org.json.JSONException
import org.json.JSONObject

internal class ScreenProcessor {

    private val logger = ConsoleLogger()

    fun processScreen(
        html: String,
        onComplete: (processedHtml: String) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        val regex = Regex(MACROS_REGEX)
        val results = regex.findAll(html, 0)
        val macroses = convertMatchResultToMacros(results.toList())

        processMacroses(html,
            macroses,
            {
                onComplete(it)
            },
            {
                onError(it)
            })
    }

    private fun convertMatchResultToMacros(
        matchResults: List<MatchResult>
    ): List<Macros> {
        val result = mutableListOf<Macros>()

        matchResults.forEach {
            val value = it.groupValues.first()
            val valueWithoutBrackets =
                value.drop(MACROS_BRACKETS_NUMBER).dropLast(MACROS_BRACKETS_NUMBER)

            try {
                val json = JSONObject(valueWithoutBrackets)

                val category = json.getString(MACROS_CATEGORY_KEY)
                val type = json.getString(MACROS_TYPE_KEY)
                val id = json.getString(MACROS_ID_KEY)

                if (category != MACROS_PRODUCT_CATEGORY || id.isEmpty()) {
                    logger.release("Invalid macros value")
                    return@forEach
                }

                val macrosType = MacrosType.fromType(type)
                val macros = Macros(macrosType, id, value)
                result.add(macros)
            } catch (e: JSONException) {
                logger.release("Failed to parse screen macros. $e")
                return@forEach
            }
        }

        return result
    }

    private fun processMacroses(
        originalHtml: String,
        macroses: List<Macros>,
        onComplete: (processedHtml: String) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        if (macroses.isEmpty()) {
            onComplete(originalHtml)
            return
        }

        Qonversion.sharedInstance.products(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) {
                var processedHtml = originalHtml

                macroses.forEach { macros ->
                    val product = products[macros.productID] ?: return@forEach

                    when (macros.type) {
                        MacrosType.Price -> {
                            product.prettyPrice?.let {
                                processedHtml =
                                    processedHtml.replace(macros.originalMacrosString, it)
                            }
                        }
                        else -> Unit
                    }
                }
                onComplete(processedHtml)
            }

            override fun onError(error: QonversionError) {
                onError(error)
            }
        })
    }

    companion object {
        private const val MACROS_REGEX = "\\[\\[.*?\\]\\]"

        private const val MACROS_PRODUCT_CATEGORY = "product"
        private const val MACROS_TYPE_KEY = "type"
        private const val MACROS_ID_KEY = "uid"
        private const val MACROS_CATEGORY_KEY = "category"

        private const val MACROS_BRACKETS_NUMBER = 2
    }
}
