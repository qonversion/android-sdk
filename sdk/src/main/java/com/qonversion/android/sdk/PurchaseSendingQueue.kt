package com.qonversion.android.sdk

import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.Storage
import com.qonversion.android.sdk.storage.purchase.PurchaseDataSource
import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.processors.PublishProcessor

class PurchaseSendingQueue(
    private val purchaseDataSource: PurchaseDataSource,
    private val api: Api,
    private val requestFactory: RequestFactory,
    private val storage: Storage,
    private val logger: Logger,
    private val scheduler: Scheduler,
    private val errorHandler: ErrorHandler
) {

    private val publishProcessor: PublishProcessor<Purchase> = PublishProcessor.create()

    fun purchasesQueue() : Flowable<Long> {
        return publishProcessor
            .onBackpressureBuffer()
            .observeOn(scheduler)
            .map {
                android.util.Pair.create(it, purchaseDataSource.isPurchaseExist(it))
            }
            .doOnNext {
                logger.log("isPurchaseExist name: ${it.first.title} exist: ${it.second}")
            }
            .filter { !it.second }
            .doOnNext {
                logger.log("filter")
            }
            .flatMapSingle { pair ->
                api.purchase(
                    requestFactory.createPurchaseRequest(pair.first, storage.load())
                ).map {
                    android.util.Pair.create(pair.first, State.SUCCESS)
                }
                 .onErrorReturn { android.util.Pair.create(pair.first, errorHandler.handle(it)) }
            }
            .doOnNext {
                logger.log("Server response state: ${it.second}")
            }
            .filter { it.second == State.SUCCESS || it.second == State.DUPLICATE }
            .map {
                purchaseDataSource.savePurchase(it.first)
            }
            .subscribeOn(scheduler)
    }

    fun addPurchase(purchase: Purchase) {
        publishProcessor.onNext(purchase)
    }

    enum class State {
        SUCCESS, DUPLICATE, SERVER_ERROR
    }
}