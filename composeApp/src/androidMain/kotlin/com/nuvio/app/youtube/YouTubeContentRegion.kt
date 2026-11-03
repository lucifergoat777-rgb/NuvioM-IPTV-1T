package com.nuvio.app.youtube

import android.content.Context
import com.liskovsoft.googlecommon.common.locale.LocaleManager

/** Which country's videos YouTube should show. Blank means the device's own region. */
internal object YouTubeContentRegion {
    /** Applies the saved choice before the YouTube feeds load. */
    fun apply(context: Context) {
        LocaleManager.setCountryOverride(YouTubeSettings.contentRegion(context).takeIf { it.isNotBlank() })
    }
}
