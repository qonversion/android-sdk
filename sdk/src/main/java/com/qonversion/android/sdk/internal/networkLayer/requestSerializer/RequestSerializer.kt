package com.qonversion.android.sdk.internal.networkLayer.requestSerializer

interface RequestSerializer {

    fun serialize(data: Map<String, Any?>): String

    fun deserialize(payload: String): Any
}
