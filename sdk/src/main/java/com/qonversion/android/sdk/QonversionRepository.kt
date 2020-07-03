package com.qonversion.android.sdk

import android.app.Application
import androidx.room.Room
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.api.RxErrorHandlingCallAdapterFactory
import com.qonversion.android.sdk.dto.AttributionRequest
import com.qonversion.android.sdk.dto.BaseResponse
import com.qonversion.android.sdk.dto.QonversionRequest
import com.qonversion.android.sdk.dto.Response
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.extractor.Extractor
import com.qonversion.android.sdk.extractor.TokenExtractor
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.Storage
import com.qonversion.android.sdk.storage.db.QonversionDatabase
import com.qonversion.android.sdk.storage.purchase.PurchaseLocalDataSource
import com.qonversion.android.sdk.validator.RequestValidator
import com.qonversion.android.sdk.validator.Validator
import com.squareup.moshi.Moshi
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit


internal class QonversionRepository private constructor(
    private val api: Api,
    private var storage: Storage,
    private val logger: Logger,
    private val requestQueue: RequestsQueue,
    private val tokenExtractor: Extractor<retrofit2.Response<BaseResponse<Response>>>,
    private val requestValidator: Validator<QonversionRequest>,
    private val requestFactory: RequestFactory,
    private val purchaseSendingQueue: PurchaseSendingQueue
) {

    private val disposable = CompositeDisposable()

    init {
        disposable.add(
                purchaseSendingQueue.purchasesQueue()
                .subscribe({
                    kickRequestQueue()
                }, {
                    logger.log("onError: $it")
                })
        )
    }

    fun init(callback: QonversionCallback?) {
        initRequest(callback, null)
    }

    fun init(edfa: String, callback: QonversionCallback?) {
        initRequest(callback, edfa)
    }

    fun purchase(
        purchase: Purchase
    ) {
        purchaseSendingQueue.addPurchase(purchase)
    }

    fun attribution(conversionInfo: Map<String, Any>, from: String, conversionUid: String) {
        val attributionRequest = requestFactory.createAttributionRequest(
            conversionInfo = conversionInfo,
            from = from,
            conversionUid = conversionUid,
            uid = storage.load()
        )
        if (requestValidator.valid(attributionRequest)) {
            logger.log("QonversionRepository: request: [${attributionRequest.javaClass.simpleName}] authorized: [TRUE]")
            sendQonversionRequest(attributionRequest)
        } else {
            logger.log("QonversionRepository: request: [${attributionRequest.javaClass.simpleName}] authorized: [FALSE]")
            requestQueue.add(attributionRequest)
        }
    }

    private fun sendQonversionRequest(request: QonversionRequest) {
        when (request) {
            is AttributionRequest -> {
                api.attribution(request).enqueue {
                    onResponse = {
                        logger.log("QonversionRequest - success - $it")
                        kickRequestQueue()
                    }

                    onFailure = {
                        logger.log("QonversionRequest - failure - $it")
                    }
                }
            }
        }
    }

    private fun kickRequestQueue() {
        val clientUid = storage.load()
        if (clientUid.isNotEmpty() && !requestQueue.isEmpty()) {
            logger.log("QonversionRepository: kickRequestQueue queue is not empty")
            val request = requestQueue.poll()
            logger.log("QonversionRepository: kickRequestQueue next request ${request?.javaClass?.simpleName}")
            if (request != null) {
                request.authorize(clientUid)
                sendQonversionRequest(request)
            }
        }
    }

    private fun initRequest(callback: QonversionCallback?, edfa: String?) {
        val initRequest = requestFactory.createInitRequest(edfa, storage.load())
        api.init(initRequest).enqueue {
            onResponse = {
                logger.log("initRequest - success - $it")
                val savedUid = saveUid(it)
                callback?.onSuccess(savedUid)
                kickRequestQueue()
            }
            onFailure = {
                if (it != null) {
                    logger.log("initRequest - failure - $it")
                    callback?.onError(it)
                }
            }
        }
    }

    private fun saveUid(response: retrofit2.Response<BaseResponse<Response>>): String {
        val token = tokenExtractor.extract(response)
        storage.save(token)
        return token
    }

    companion object {

        private const val BASE_URL = "https://api.qonversion.io/"
        private const val TIMEOUT = 30L
        private const val CACHE_SIZE = 10485776L //10 MB

        fun initialize(
            context: Application,
            storage: Storage,
            logger: Logger,
            environmentProvider: EnvironmentProvider,
            config: QonversionConfig,
            internalUserId: String
        ): QonversionRepository {

            val client = OkHttpClient.Builder()
                .cache(Cache(context.cacheDir, CACHE_SIZE))
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
                .addCallAdapterFactory(RxErrorHandlingCallAdapterFactory.create())
                .baseUrl(BASE_URL)
                .client(client)
                .build()

            val requestQueue = RequestsQueue(logger)

            val database : QonversionDatabase = Room.databaseBuilder(
                context,
                QonversionDatabase::class.java,
                QonversionDatabase.DATABASE_NAME
            ).fallbackToDestructiveMigrationOnDowngrade()
                .enableMultiInstanceInvalidation()
                .build()

            val purchaseLocalDataSource = PurchaseLocalDataSource(
                database.purchaseInfo()
            )

            val requestFactory = RequestFactory(
                environmentProvider = environmentProvider,
                internalUserId = internalUserId,
                key = config.key,
                sdkVersion = config.sdkVersion,
                trackingEnabled = config.trackingEnabled
            )

            val api =  retrofit.create(Api::class.java)

            val purchaseSendingQueue =
                PurchaseSendingQueue(
                    purchaseLocalDataSource,
                    api,
                    requestFactory,
                    storage,
                    logger,
                    Schedulers.io(),
                    ErrorHandler(logger)
                )

            return QonversionRepository(
                api,
                storage,
                logger,
                requestQueue,
                TokenExtractor(),
                RequestValidator(),
                requestFactory,
                purchaseSendingQueue
            )
        }
    }
}
