package com.nuvio.app.youtube

import android.content.Context

/** Your YouTube choices, kept on this device. */
object YouTubeSettings {
    private const val PREFS = "youtube_settings"
    private const val KEY_SPONSORBLOCK = "sponsorblock_enabled"
    private const val KEY_CATEGORIES = "sponsorblock_categories"
    private const val KEY_QUALITY = "preferred_height"
    private const val KEY_HIDDEN_TABS = "hidden_tabs"
    private const val KEY_AUTOPLAY = "autoplay_next"
    private const val KEY_HIDE_SHORTS = "hide_shorts"
    private const val KEY_HIDDEN_WORDS = "hidden_keywords"
    private const val KEY_HIDDEN_CHANNELS = "hidden_channels"
    private const val KEY_LAST_TAB = "last_tab"
    private const val KEY_TAB_ORDER = "tab_order"
    private const val KEY_REMEMBER_TAB = "remember_last_tab"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_SPONSOR_NOTICE = "sponsorblock_notice"
    private const val KEY_SPONSOR_ASK = "sponsorblock_ask"
    private const val KEY_DEARROW_TITLES = "dearrow_titles"
    private const val KEY_DEARROW_THUMBS = "dearrow_thumbnails"
    private const val KEY_HIDE_WATCHED = "hide_watched"
    private const val KEY_HIDE_UPCOMING = "hide_upcoming"
    private const val KEY_CODEC = "codec_preference"
    private const val KEY_FEED_PREVIEW = "feed_preview"
    private const val KEY_CONTENT_REGION = "content_region"

    /** SponsorBlock's segment types, in the order they're listed. */
    val sponsorCategories: List<Pair<String, String>> = listOf(
        "sponsor" to "Sponsor",
        "selfpromo" to "Self-promotion",
        "interaction" to "Like and subscribe reminders",
        "intro" to "Intros",
        "outro" to "Outros and end cards",
        "preview" to "Previews and recaps",
        "music_offtopic" to "Non-music parts",
        "filler" to "Filler"
    )

    private val defaultCategories = setOf("sponsor", "selfpromo", "interaction")

    /** The sizes you can set as a default; 0 is automatic. */
    val qualityChoices = listOf(0, 2160, 1440, 1080, 720, 480, 360)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun sponsorBlockEnabled(context: Context) = prefs(context).getBoolean(KEY_SPONSORBLOCK, true)

    fun setSponsorBlockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SPONSORBLOCK, enabled).apply()
    }

    fun sponsorCategoriesOn(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_CATEGORIES, null)?.toSet() ?: defaultCategories

    fun setSponsorCategory(context: Context, category: String, on: Boolean) {
        val next = sponsorCategoriesOn(context).toMutableSet().apply { if (on) add(category) else remove(category) }
        prefs(context).edit().putStringSet(KEY_CATEGORIES, next).apply()
    }

    /** Names of the tabs you've turned off on the YouTube page. */
    fun hiddenTabs(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN_TABS, null)?.toSet() ?: emptySet()

    fun setTabVisible(context: Context, tab: YtTab, visible: Boolean) {
        val next = hiddenTabs(context).toMutableSet().apply { if (visible) remove(tab.name) else add(tab.name) }
        prefs(context).edit().putStringSet(KEY_HIDDEN_TABS, next).apply()
    }

    /** Every tab in the order you've set. Tabs added in a later version go on the end. */
    fun tabOrder(context: Context): List<YtTab> {
        val saved = prefs(context).getString(KEY_TAB_ORDER, null)
            ?.split(',')
            ?.mapNotNull { name -> YtTab.entries.firstOrNull { it.name == name } }
            .orEmpty()
            .distinct()
        return saved + YtTab.entries.filter { it !in saved }
    }

    fun setTabOrder(context: Context, order: List<YtTab>) =
        prefs(context).edit().putString(KEY_TAB_ORDER, order.joinToString(",") { it.name }).apply()

    /** The tabs to show, in your order. There is always at least one. */
    fun visibleTabs(context: Context): List<YtTab> {
        val hidden = hiddenTabs(context)
        return tabOrder(context).filter { it.name !in hidden }.ifEmpty { listOf(YtTab.HOME) }
    }

    /** The tab you were last on, if it's still switched on; otherwise the first one. */
    fun lastTab(context: Context): YtTab {
        val visible = visibleTabs(context)
        if (!rememberLastTab(context)) return visible.first()
        val saved = prefs(context).getString(KEY_LAST_TAB, null)
        return visible.firstOrNull { it.name == saved } ?: visible.first()
    }

    /** Open on the tab you were last on; off means the first tab each time. */
    fun rememberLastTab(context: Context) = prefs(context).getBoolean(KEY_REMEMBER_TAB, true)
    fun setRememberLastTab(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_REMEMBER_TAB, on).apply()

    /** The playback speed you last picked on a YouTube video, used to start the next one. */
    fun playbackSpeed(context: Context) = prefs(context).getFloat(KEY_SPEED, 1f)
    fun setPlaybackSpeed(context: Context, speed: Float) = prefs(context).edit().putFloat(KEY_SPEED, speed).apply()

    fun setLastTab(context: Context, tab: YtTab) = prefs(context).edit().putString(KEY_LAST_TAB, tab.name).apply()

    /** Ask before skipping a SponsorBlock segment, instead of skipping it straight away. */
    fun sponsorAsk(context: Context) = prefs(context).getBoolean(KEY_SPONSOR_ASK, false)
    fun setSponsorAsk(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_SPONSOR_ASK, on).apply()

    /** Whether skipping a SponsorBlock segment shows a short notice. */
    fun sponsorNotice(context: Context) = prefs(context).getBoolean(KEY_SPONSOR_NOTICE, true)
    fun setSponsorNotice(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_SPONSOR_NOTICE, on).apply()

    /** DeArrow: community titles and thumbnails in place of clickbait. */
    fun deArrowTitles(context: Context) = prefs(context).getBoolean(KEY_DEARROW_TITLES, true)
    fun deArrowThumbnails(context: Context) = prefs(context).getBoolean(KEY_DEARROW_THUMBS, true)
    fun setDeArrowTitles(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_DEARROW_TITLES, on).apply()
    fun setDeArrowThumbnails(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_DEARROW_THUMBS, on).apply()

    /** Words that keep a video off the page when they're in its title or channel name. Lower case. */
    fun hiddenKeywords(context: Context): List<String> =
        (prefs(context).getStringSet(KEY_HIDDEN_WORDS, null) ?: emptySet()).sorted()

    fun addHiddenKeyword(context: Context, word: String) {
        val clean = word.trim().lowercase()
        if (clean.isBlank()) return
        prefs(context).edit().putStringSet(KEY_HIDDEN_WORDS, (hiddenKeywords(context) + clean).toSet()).apply()
    }

    fun removeHiddenKeyword(context: Context, word: String) {
        prefs(context).edit().putStringSet(KEY_HIDDEN_WORDS, (hiddenKeywords(context) - word).toSet()).apply()
    }

    /** Channels you've hidden, as (id, name). */
    fun hiddenChannels(context: Context): List<Pair<String, String>> =
        (prefs(context).getStringSet(KEY_HIDDEN_CHANNELS, null) ?: emptySet())
            .mapNotNull { entry -> entry.split('|', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .sortedBy { it.second.lowercase() }

    fun hideChannel(context: Context, id: String, name: String) {
        val kept = hiddenChannels(context).filter { it.first != id } + (id to name)
        prefs(context).edit().putStringSet(KEY_HIDDEN_CHANNELS, kept.map { "${it.first}|${it.second}" }.toSet()).apply()
    }

    fun unhideChannel(context: Context, id: String) {
        val kept = hiddenChannels(context).filter { it.first != id }
        prefs(context).edit().putStringSet(KEY_HIDDEN_CHANNELS, kept.map { "${it.first}|${it.second}" }.toSet()).apply()
    }

    /** A short muted preview of the video in view, in the feeds. On by default. */
    fun feedPreview(context: Context) = prefs(context).getBoolean(KEY_FEED_PREVIEW, true)
    fun setFeedPreview(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_FEED_PREVIEW, on).apply()

    /** The country whose videos show in News and Live; blank follows the device region. */
    fun contentRegion(context: Context): String = prefs(context).getString(KEY_CONTENT_REGION, "") ?: ""
    fun setContentRegion(context: Context, region: String) =
        prefs(context).edit().putString(KEY_CONTENT_REGION, region.trim().uppercase()).apply()

    /** Feed filters. Upcoming premieres can't be played yet, so they're hidden unless you say otherwise. */
    fun hideShorts(context: Context) = prefs(context).getBoolean(KEY_HIDE_SHORTS, false)
    fun hideWatched(context: Context) = prefs(context).getBoolean(KEY_HIDE_WATCHED, false)
    fun hideUpcoming(context: Context) = prefs(context).getBoolean(KEY_HIDE_UPCOMING, true)

    fun setHideShorts(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_HIDE_SHORTS, on).apply()
    fun setHideWatched(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_HIDE_WATCHED, on).apply()
    fun setHideUpcoming(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_HIDE_UPCOMING, on).apply()

    /** Play the next video when one ends. */
    fun autoplayNext(context: Context) = prefs(context).getBoolean(KEY_AUTOPLAY, true)

    fun setAutoplayNext(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOPLAY, on).apply()
    }

    /** Which video format to prefer when YouTube offers the same size in several. */
    val codecChoices = listOf("auto", "avc", "vp9", "av1")

    fun codecPreference(context: Context): String =
        prefs(context).getString(KEY_CODEC, "auto")?.takeIf { it in codecChoices } ?: "auto"

    fun setCodecPreference(context: Context, codec: String) {
        prefs(context).edit().putString(KEY_CODEC, codec).apply()
    }

    fun codecLabel(codec: String) = when (codec) {
        "avc" -> "H.264 (most compatible)"
        "vp9" -> "VP9"
        "av1" -> "AV1"
        else -> "Auto"
    }

    /** Video MIME types in the order to try them. Auto leaves AV1 for last: few boxes decode it well. */
    fun codecMimeOrder(context: Context): List<String> {
        val vp9 = "video/x-vnd.on2.vp9"
        val avc = "video/avc"
        val av1 = "video/av01"
        return when (codecPreference(context)) {
            "avc" -> listOf(avc, vp9, av1)
            "vp9" -> listOf(vp9, avc, av1)
            "av1" -> listOf(av1, vp9, avc)
            else -> listOf(vp9, avc, av1)
        }
    }

    /** The video height to start at; 0 lets the player choose. */
    fun preferredHeight(context: Context) = prefs(context).getInt(KEY_QUALITY, 0)

    fun setPreferredHeight(context: Context, height: Int) {
        prefs(context).edit().putInt(KEY_QUALITY, height).apply()
    }

    fun qualityLabel(height: Int) = if (height <= 0) "Auto" else "${height}p"
}
