package com.nuvio.app.livetv.data

import com.nuvio.app.livetv.model.LiveChannel

/** The bit of the TV app's poster lookup that Sports needs: spotting 24/7 channels. */
object LiveTvPosterResolver {
    private val TWENTY_FOUR_SEVEN = Regex("""(?i)\b24\s*[/\|\-x]?\s*7\b""")

    /** A 24/7 channel (by its name or its group, or the playlist's own names). */
    fun is247(channel: LiveChannel): Boolean =
        TWENTY_FOUR_SEVEN.containsMatchIn(channel.name) || TWENTY_FOUR_SEVEN.containsMatchIn(channel.group) ||
            TWENTY_FOUR_SEVEN.containsMatchIn(channel.key) || TWENTY_FOUR_SEVEN.containsMatchIn(channel.groupId) ||
            (channel.tvgName?.let { TWENTY_FOUR_SEVEN.containsMatchIn(it) } ?: false)
}
