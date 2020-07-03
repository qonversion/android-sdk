package com.qonversion.android.sdk

import com.qonversion.android.sdk.api.RetrofitException
import com.qonversion.android.sdk.logger.Logger
import org.json.JSONObject

class ErrorHandler(
   private val logger: Logger
) {

    fun handle(
        it: Throwable
    ): PurchaseSendingQueue.State {
        if (it is RetrofitException) {
            val body = it.response?.errorBody()?.string()
            return getState(body)

        }
        return PurchaseSendingQueue.State.SERVER_ERROR
    }

    private fun getState(body: String?): PurchaseSendingQueue.State {
        if (body == null) {
            return PurchaseSendingQueue.State.SERVER_ERROR
        }
        try {
            val jsonObj = JSONObject(body)
            val success = jsonObj.getBoolean("success")
            if (!success) {
                return PurchaseSendingQueue.State.DUPLICATE
            }
        } catch (t: Throwable) {
            logger.log("Json parsing error - body: $body - throwable: $t")
            return PurchaseSendingQueue.State.SERVER_ERROR
        }
        return PurchaseSendingQueue.State.SERVER_ERROR
    }
}