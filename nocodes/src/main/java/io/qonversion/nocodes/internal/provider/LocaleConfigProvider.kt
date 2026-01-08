package io.qonversion.nocodes.internal.provider

internal interface LocaleConfigProvider {
    /**
     * Custom locale set by the client. If null, the system default locale will be used.
     */
    var customLocale: String?
}
