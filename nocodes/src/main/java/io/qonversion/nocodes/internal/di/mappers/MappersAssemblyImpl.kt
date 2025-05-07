package io.qonversion.nocodes.internal.di.mappers

import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.internal.common.mappers.ActionMapper
import io.qonversion.nocodes.internal.common.mappers.Mapper
import io.qonversion.nocodes.internal.common.mappers.ScreenMapper
import io.qonversion.nocodes.internal.common.mappers.error.ApiErrorMapper
import io.qonversion.nocodes.internal.common.mappers.error.ErrorResponseMapper
import io.qonversion.nocodes.internal.dto.NoCodeScreen

internal class MappersAssemblyImpl : MappersAssembly {

    override fun apiErrorMapper(): ErrorResponseMapper = ApiErrorMapper()

    override fun screenMapper(): Mapper<NoCodeScreen?> = ScreenMapper()

    override fun actionMapper(): Mapper<QAction> = ActionMapper()
}
