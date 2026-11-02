package com.nuvio.app.livetv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.livetv.LiveTvBell
import com.nuvio.app.livetv.LiveTv

/** The bell on a show's page: follows or un-follows it in the Calendar. */
@Composable
fun rememberLiveTvBell(imdb: String, name: String): LiveTvBell {
    val context = LocalContext.current.applicationContext
    val calendar = remember { LiveTv.get(context).calendar }
    val state by calendar.state.collectAsStateWithLifecycle()
    // The lists load with the first refresh.
    LaunchedEffect(Unit) { calendar.refresh() }
    return LiveTvBell(on = imdb in state.followed, toggle = { calendar.toggleFollow(imdb, name) })
}
