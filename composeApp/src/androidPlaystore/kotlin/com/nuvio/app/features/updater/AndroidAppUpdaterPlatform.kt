package com.nuvio.app.features.updater

import android.content.Context

object AndroidAppUpdaterPlatform {
    private const val preferencesName = "nuvio_updater"
    private const val whatsNewCacheKey = "whats_new_cache"
    private const val whatsNewSeenVersionKey = "whats_new_seen_version"

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getWhatsNewSeenVersion(): String? =
        appContext?.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            ?.getString(whatsNewSeenVersionKey, null)

    fun setWhatsNewSeenVersion(version: String?) {
        appContext?.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)?.edit()?.apply {
            if (version == null) remove(whatsNewSeenVersionKey) else putString(whatsNewSeenVersionKey, version)
        }?.apply()
    }

    fun getWhatsNewCache(): String? =
        appContext?.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            ?.getString(whatsNewCacheKey, null)

    fun setWhatsNewCache(payload: String?) {
        appContext?.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)?.edit()?.apply {
            if (payload == null) remove(whatsNewCacheKey) else putString(whatsNewCacheKey, payload)
        }?.apply()
    }
}
