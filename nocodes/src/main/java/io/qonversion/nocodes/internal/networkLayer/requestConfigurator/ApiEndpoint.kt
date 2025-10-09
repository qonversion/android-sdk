package io.qonversion.nocodes.internal.networkLayer.requestConfigurator

internal enum class ApiEndpoint(val path: String) {
    Screens("screens"),
    Contexts("contexts"),
    Preload("screens")
}
