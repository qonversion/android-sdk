package com.qonversion.android.sdk.internal.user.generator

import java.util.UUID

private const val USER_ID_PREFIX = "QON"
private const val USER_ID_SEPARATOR = "_"

class UserIdGeneratorImpl : UserIdGenerator {

    override fun generate(): String {
        val uuid = UUID.randomUUID().toString().replace(Regex("-"), "")

        return "${USER_ID_PREFIX}${USER_ID_SEPARATOR}$uuid"
    }
}
