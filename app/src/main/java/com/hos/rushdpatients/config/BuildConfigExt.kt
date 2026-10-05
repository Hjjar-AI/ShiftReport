package com.hos.rushdpatients.config

import com.hos.rushdpatients.BuildConfig

/**
 * Thin wrapper around generated BuildConfig so the rest of the app
 * never has to reference BuildConfig directly.
 *
 * Project credentials are runtime configuration and intentionally do not live here.
 */
object BuildConfigExt {
    val isDebug: Boolean get() = BuildConfig.DEBUG
    val appVersionName: String get() = BuildConfig.VERSION_NAME
    val appVersionCode: Int get() = BuildConfig.VERSION_CODE
}
