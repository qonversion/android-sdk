package io.qonversion.nocodes.internal.common

const val PREFS_NAME = "io.qonversion.nocodes"

internal enum class StorageConstants(val key: String) {
    SourceKey("source"),
    VersionKey("sourceVersion"),
}
