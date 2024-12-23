package io.qonversion.nocodes.internal.di.controllers

import io.qonversion.nocodes.internal.screen.controller.ScreenController
import io.qonversion.nocodes.internal.screen.view.ScreenContract

internal interface ControllersAssembly {

    fun screenController(): ScreenController

    fun screenPresenter(view: ScreenContract.View): ScreenContract.Presenter
}