package com.nuvio.app.livetv.ui

/** Settings the guide reads while drawing, kept here so it needn't collect the whole settings flow. */
object LiveTvDisplay {
    @Volatile var use24Hour = false
    @Volatile var guideSize = ""
    @Volatile var showTimes = false
    @Volatile var showEpisodes = true
    @Volatile var showNowLine = true
}
