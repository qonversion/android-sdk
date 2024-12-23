package io.qonversion.nocodes.internal.di.services

import io.qonversion.nocodes.internal.screen.service.ScreenService

internal interface ServicesAssembly {

    fun screenService(): ScreenService
}