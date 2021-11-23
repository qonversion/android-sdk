package com.qonversion.android.app

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.qonversion.android.sdk.old.QonversionError

fun showError(context: Context, error: QonversionError, logTag: String) {
    val code = error.code                           // Error enum code
    val description = error.description             // Error enum code description
    val additionalMessage =
        error.additionalMessage // Additional error information (if possible)
    Toast.makeText(context, error.description, Toast.LENGTH_LONG).show()
    Log.e(
        logTag,
        "error code: $code, description: $description, additionalMessage: $additionalMessage"
    )
}