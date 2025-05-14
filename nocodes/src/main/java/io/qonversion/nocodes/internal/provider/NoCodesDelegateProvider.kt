package io.qonversion.nocodes.internal.provider

import io.qonversion.nocodes.interfaces.NoCodesDelegate
import java.lang.ref.WeakReference

internal interface NoCodesDelegateProvider {

    var noCodesDelegate: WeakReference<NoCodesDelegate>?
}
