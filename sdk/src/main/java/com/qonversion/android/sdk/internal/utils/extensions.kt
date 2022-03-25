package com.qonversion.android.sdk.internal.utils

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo

internal val Context.isDebuggable get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

internal val Context.application get() = applicationContext as Application
