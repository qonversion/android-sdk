package io.qonversion.nocodes.internal.provider

import io.qonversion.nocodes.interfaces.NoCodesDelegate

internal interface NoCodesDelegateProvider {

    var noCodesDelegate: NoCodesDelegate?
}
