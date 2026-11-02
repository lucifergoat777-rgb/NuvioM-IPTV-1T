package com.nuvio.app.livetv.record

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A short message for the Live TV screens: a playlist about to expire, a recording that failed. */
object LiveTvNotice {
    data class Notice(val text: String, val id: Long = System.nanoTime())

    private val _current = MutableStateFlow<Notice?>(null)
    val current: StateFlow<Notice?> = _current.asStateFlow()

    fun show(text: String) { _current.value = Notice(text) }

    fun clear(notice: Notice) { if (_current.value == notice) _current.value = null }
}
