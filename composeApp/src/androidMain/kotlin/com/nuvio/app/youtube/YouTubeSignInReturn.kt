package com.nuvio.app.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens youtube.com/activate in an in-app browser tab, and brings the app back to the front once YouTube
 * reports that the code was approved, so Google's "Device connected" page isn't left on screen.
 */
internal object YouTubeSignInReturn {
    private const val TAG = "YouTubeSignInReturn"

    /** True while an activate page opened from here may still be on screen. */
    @Volatile
    private var tabOpen = false

    @Volatile
    private var appContext: Context? = null

    /** Opens [link] in a Custom Tab; [fallback] opens it the usual way when that isn't possible. */
    fun openActivatePage(context: Context, link: String, fallback: () -> Unit) {
        appContext = context.applicationContext
        YouTubeSignInKeepAliveService.start(context.applicationContext)
        val opened = runCatching {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(link))
        }.onFailure { Log.w(TAG, "custom tab unavailable", it) }.isSuccess
        tabOpen = opened
        if (!opened) fallback()
    }

    /** Brings the app's own screen back over the browser tab, but only when this sign-in opened one. */
    fun backToApp(context: Context) {
        YouTubeSignInKeepAliveService.stop(context)
        if (!tabOpen) return
        tabOpen = false
        runCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(launch)
        }.onFailure { Log.w(TAG, "couldn't return to the app", it) }
    }

    /** The sign-in was cancelled, so there is nothing to return from. */
    fun cancelled() {
        tabOpen = false
        appContext?.let(YouTubeSignInKeepAliveService::stop)
    }
}
