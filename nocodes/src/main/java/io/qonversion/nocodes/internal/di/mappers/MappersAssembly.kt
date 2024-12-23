package io.qonversion.nocodes.internal.di.mappers

import io.qonversion.nocodes.internal.common.mappers.Mapper
import io.qonversion.nocodes.internal.common.mappers.error.ErrorResponseMapper
import io.qonversion.nocodes.internal.dto.NoCodeScreen

internal interface MappersAssembly {

    fun apiErrorMapper(): ErrorResponseMapper

    fun screenMapper(): Mapper<NoCodeScreen?>
}
