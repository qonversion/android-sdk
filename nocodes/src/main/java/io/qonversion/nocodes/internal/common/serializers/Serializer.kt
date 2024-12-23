package io.qonversion.nocodes.internal.common.serializers

import io.qonversion.nocodes.error.NoCodesException

internal interface Serializer {

    @Throws(NoCodesException::class)
    fun serialize(data: Map<String, Any?>): String

    @Throws(NoCodesException::class)
    fun deserialize(payload: String): Any
}
