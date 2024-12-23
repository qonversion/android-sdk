package io.qonversion.nocodes.interfaces

import io.qonversion.nocodes.error.NoCodesError

interface NoCodesShowScreenCallback {

    fun onSuccess()

    fun onError(error: NoCodesError)
}
