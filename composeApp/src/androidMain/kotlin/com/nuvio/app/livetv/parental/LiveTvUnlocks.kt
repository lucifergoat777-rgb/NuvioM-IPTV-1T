package com.nuvio.app.livetv.parental

import java.util.concurrent.ConcurrentHashMap

/**
 * Groups and On Demand categories opened with the PIN, and when. How long they stay open is a setting:
 * until you leave Live TV ([LEAVE]), for a number of minutes, or until the app closes ([UNTIL_CLOSED]).
 */
object LiveTvUnlocks {
    /** Lock again as soon as you leave the Live TV page. */
    const val LEAVE = -1

    /** Stay open until the app is closed. */
    const val UNTIL_CLOSED = 0

    private val opened = ConcurrentHashMap<String, Long>()

    fun unlock(id: String) { opened[id] = System.currentTimeMillis() }

    /** What's open now, given the setting; anything past its time is locked again. */
    fun active(relockMinutes: Int): Set<String> {
        if (relockMinutes > 0) {
            val limit = System.currentTimeMillis() - relockMinutes * 60_000L
            opened.entries.removeAll { it.value < limit }
        }
        return opened.keys.toSet()
    }

    fun lockAll() = opened.clear()
}
