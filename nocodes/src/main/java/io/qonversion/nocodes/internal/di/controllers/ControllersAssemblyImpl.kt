package io.qonversion.nocodes.internal.di.controllers

import android.content.Context
import io.qonversion.nocodes.internal.di.misc.MiscAssembly
import io.qonversion.nocodes.internal.di.services.ServicesAssembly
import io.qonversion.nocodes.internal.dto.config.InternalConfig
import io.qonversion.nocodes.internal.screen.controller.ScreenController
import io.qonversion.nocodes.internal.screen.controller.ScreenControllerImpl
import io.qonversion.nocodes.internal.screen.view.ScreenContract
import io.qonversion.nocodes.internal.screen.view.ScreenPresenter

internal class ControllersAssemblyImpl(
    private val servicesAssembly: ServicesAssembly,
    private val miscAssembly: MiscAssembly,
    private val internalConfig: InternalConfig,
    private val appContext: Context
) : ControllersAssembly {

    override fun screenController(): ScreenController {
        return ScreenControllerImpl(
            servicesAssembly.screenService(),
            internalConfig,
            miscAssembly.activityProvider(),
            appContext,
            miscAssembly.logger()
        )
    }

    override fun screenPresenter(view: ScreenContract.View): ScreenContract.Presenter {
        return ScreenPresenter(servicesAssembly.screenService(), view, miscAssembly.logger())
    }
}
