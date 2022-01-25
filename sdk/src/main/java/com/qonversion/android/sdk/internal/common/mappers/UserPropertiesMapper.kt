package com.qonversion.android.sdk.internal.common.mappers

internal class UserPropertiesMapper : Mapper<List<String>> {
    override fun fromMap(data: Map<*, *>): List<String> {
        val handledProperties = data.getList("processed")
        val result: List<String> = handledProperties?.map { it.toString() } ?: emptyList()

        return result
    }
}
