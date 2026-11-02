package com.nuvio.app.livetv.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which profile's Live TV data to use. The TV app has up to six; here there is one until the profiles are wired in. */
class LiveTvProfile {
    private val _activeProfileId = MutableStateFlow(1)
    val activeProfileId: StateFlow<Int> = _activeProfileId.asStateFlow()
}
