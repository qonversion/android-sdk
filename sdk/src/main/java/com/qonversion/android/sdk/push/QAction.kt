package com.qonversion.android.sdk.push

data class QAction(
    val type: QActionType,
    val value: Map<String, String>? = null
)