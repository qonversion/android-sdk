package io.qonversion.sample

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.qonversion.android.sdk.dto.QonversionError

fun showError(context: Context, error: QonversionError, logTag: String) {
    Toast.makeText(context, error.description, Toast.LENGTH_LONG).show()
    val msg = "error code: ${error.code}, description: ${error.description}, additionalMessage: ${error.additionalMessage}"
    Log.e(logTag, msg)
}
