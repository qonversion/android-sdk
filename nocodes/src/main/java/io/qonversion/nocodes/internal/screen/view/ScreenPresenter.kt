package io.qonversion.nocodes.internal.screen.view

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductPrice
import com.qonversion.android.sdk.dto.products.QProductPricingPhase
import com.qonversion.android.sdk.dto.products.QSubscriptionPeriod
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesError
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.common.BaseClass
import io.qonversion.nocodes.internal.common.mappers.Mapper
import io.qonversion.nocodes.internal.common.serializers.Serializer
import io.qonversion.nocodes.internal.logger.Logger
import io.qonversion.nocodes.internal.provider.NoCodesDelegateProvider
import io.qonversion.nocodes.internal.screen.service.ScreenService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class ScreenPresenter(
    private val service: ScreenService,
    private val view: ScreenContract.View,
    logger: Logger,
    private val delegateProvider: NoCodesDelegateProvider,
    private val serializer: Serializer,
    private val actionMapper: Mapper<QAction>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : ScreenContract.Presenter, BaseClass(logger) {

    override fun onStart(contextKey: String?, screenId: String?) {
        scope.launch {
            logger.verbose("ScreenPresenter -> loading the screen to present")

            val screen = try { contextKey?.let {
                    service.getScreen(contextKey)
                } ?: screenId?.let {
                    service.getScreenById(screenId)
                } ?: run {
                    val errorMessage =
                        "Neither context key, nor screen id is provided to load screen"
                    logger.error("ScreenPresenter -> $errorMessage")

                    val error = NoCodesError(ErrorCode.ScreenNotFound, errorMessage)
                    delegateProvider.noCodesDelegate?.get()?.onScreenFailedToLoad(error)

                    return@launch
                }
            } catch (e: NoCodesException) {
                logger.error("ScreenPresenter -> Failed to fetch No-Code screen. $e")

                delegateProvider.noCodesDelegate?.get()?.onScreenFailedToLoad(NoCodesError(e))

                return@launch
            }

            logger.verbose("ScreenPresenter -> displaying the screen with id ${screen.id}, context key: ${screen.contextKey}")
            view.displayScreen(screen.id, screen.body)
        }
    }

    override fun onWebViewMessageReceived(message: String) {
        val action = try {
            val data = serializer.deserialize(message) as Map<*, *>
            actionMapper.fromMap(data["data"] as Map<*, *>)
        } catch (e: Exception) {
            logger.error("ScreenPresenter -> failed to map action from web page: $e. Original message: $message")
            return
        }

        logger.verbose("ScreenPresenter -> handling action with type ${action.type}")
        when (action.type) {
            QAction.Type.Url -> {
                action.parameters?.get(QAction.Parameter.Url)?.let { link ->
                    view.openLink(link as String)
                }
            }
            QAction.Type.DeepLink -> {
                action.parameters?.get(QAction.Parameter.Deeplink)?.let { deeplink ->
                    view.openDeepLink(deeplink as String)
                }
            }
            QAction.Type.Close -> {
                view.close()
            }
            QAction.Type.CloseAll -> {
                view.closeAll()
            }
            QAction.Type.LoadProducts -> {
                handleLoadProductsAction(action)
            }
            QAction.Type.ShowScreen -> {
                view.finishScreenPreparation()
            }
            QAction.Type.Navigation -> {
                action.parameters?.get(QAction.Parameter.ScreenId)?.let { screenId ->
                    loadNextScreen(screenId as String)
                }
            }
            QAction.Type.Purchase -> {
                action.parameters?.get(QAction.Parameter.ProductId)?.let { productId ->
                    view.purchase(productId as String)
                }
            }
            QAction.Type.Restore -> {
                view.restore()
            }
            else -> {
                logger.warn("ScreenPresenter -> action type ${action.type} is not supported")
            }
        }
        return
    }

    private fun handleLoadProductsAction(action: QAction) {
        val productIds = action.parameters?.get(QAction.Parameter.ProductIds) as? List<*>

        if (productIds?.isEmpty() != false) {
            return
        }

        Qonversion.shared.products(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) {
                val filteredProducts = products.filterKeys { productIds.contains(it) }

                val productsInfo = filteredProducts.mapValues { entry -> mapProductInfo(entry.value) }
                val data = mapOf("data" to productsInfo)

                val jsonData = serializer.serialize(data)

                view.sendProductsToWebView(jsonData)
            }

            override fun onError(error: QonversionError) {
                logger.error("Failed to load products for the screen:\n$error")
            }
        })
    }

    private fun mapProductInfo(product: QProduct): Map<String, Any?> {
        val res = mutableMapOf<String, Any?>(
            "id" to product.qonversionID,
            "store_id" to product.storeID,
        )

        fun enrichWithPriceDetails(price: QProductPrice) {
            res["price"] = price.priceAmount
            res["currency_symbol"] = price.currencySymbol
            res["currency_code"] = price.priceCurrencyCode
        }

        fun mapPeriodUnit(periodUnit: QSubscriptionPeriod.Unit): String? {
            return when (periodUnit) {
                QSubscriptionPeriod.Unit.Day -> "day"
                QSubscriptionPeriod.Unit.Week -> "week"
                QSubscriptionPeriod.Unit.Month -> "month"
                QSubscriptionPeriod.Unit.Year -> "year"
                QSubscriptionPeriod.Unit.Unknown -> null
            }
        }

        product.storeDetails?.let { storeDetails ->
            res["title"] = storeDetails.title

            if (storeDetails.isInApp) {
                storeDetails.inAppOfferDetails?.price?.let {
                    enrichWithPriceDetails(it)
                }
            } else {
                storeDetails.defaultSubscriptionOfferDetails?.let { subscriptionOfferDetails ->
                    subscriptionOfferDetails.basePlan?.let { basePlan ->
                        enrichWithPriceDetails(basePlan.price)
                        res["period_unit"] = mapPeriodUnit(basePlan.billingPeriod.unit)
                        res["period_unit_count"] = basePlan.billingPeriod.unitCount
                    }

                    val introPhase = subscriptionOfferDetails.trialPhase
                        ?: subscriptionOfferDetails.introPhase

                    introPhase?.let {
                        res["payment_mode"] = if (introPhase.isTrial) "trial" else "intro"
                        res["intro_price"] = it.price.priceAmount
                        res["intro_price_type"] = when (it.type) {
                            QProductPricingPhase.Type.FreeTrial -> "trial"
                            QProductPricingPhase.Type.DiscountedSinglePayment -> "pay_up_front"
                            QProductPricingPhase.Type.DiscountedRecurringPayment -> "pay_as_you_go"
                            else -> null
                        }
                        res["intro_period_unit"] = mapPeriodUnit(it.billingPeriod.unit)
                        res["intro_period_unit_count"] = it.billingPeriod.unitCount
                        res["intro_number_of_periods"] = it.billingCycleCount
                    }
                }
            }
        }

        return res
    }

    private fun loadNextScreen(screenId: String) {
        try {
            scope.launch {
                logger.verbose("ScreenPresenter -> opening the screen with id $screenId")
                view.navigateToScreen(screenId)
            }
        } catch (e: Exception) {
            logger.error("ScreenPresenter -> failed to open the screen with id $screenId")
        }
    }
}
