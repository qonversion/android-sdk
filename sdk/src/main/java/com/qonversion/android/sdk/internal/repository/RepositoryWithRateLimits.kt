package com.qonversion.android.sdk.internal.repository

internal class RepositoryWithRateLimits(
    private val repository: QRepository
): QRepository by repository
