package com.nuvio.app.cast

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.nuvio.app.features.cast.CastHost
import com.nuvio.app.features.cast.CastLoad
import com.nuvio.app.features.cast.CastUi
import java.util.concurrent.Executors

/**
 * Google Cast for the shared player. The player hands over whatever it is playing; the Cast device
 * fetches that link itself, so links only this phone can reach (its own P2P or YouTube server, local
 * files) can't be sent.
 */
object CastController {
    private const val TAG = "NuvioCast"
    private var context: CastContext? = null
    private var activity: Activity? = null

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { activity = a }
            override fun onActivityPaused(a: Activity) { if (activity === a) activity = null }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        // Needs Google Play services: without them there is simply no Cast button.
        runCatching {
            CastContext.getSharedInstance(app, Executors.newSingleThreadExecutor()).addOnSuccessListener { ctx ->
                context = ctx
                ctx.sessionManager.addSessionManagerListener(sessions, CastSession::class.java)
                CastHost.chooser = { showDialog(ctx) }
                CastHost.load = { req -> send(ctx, req) }
                CastHost.togglePlayback = { client(ctx)?.togglePlayback() }
                CastHost.seekTo = { ms -> client(ctx)?.seek(MediaSeekOptions.Builder().setPosition(ms.coerceAtLeast(0L)).build()) }
                CastHost.stop = { ctx.sessionManager.endCurrentSession(true) }
                CastHost.selectAudio = { id ->
                    val rmc = client(ctx)
                    val tracks = rmc?.mediaInfo?.mediaTracks.orEmpty()
                    // The other kinds of track (video, captions) stay as they are; only the sound changes.
                    val current = rmc?.mediaStatus?.activeTrackIds?.toList().orEmpty()
                    val keep = current.filter { a -> tracks.none { it.id == a && it.type == com.google.android.gms.cast.MediaTrack.TYPE_AUDIO } }
                    rmc?.setActiveMediaTracks((keep + id).toLongArray())
                }
                CastHost.setVolume = { v -> runCatching { ctx.sessionManager.currentCastSession?.volume = v.toDouble().coerceIn(0.0, 1.0) } }
                CastHost.setMuted = { m -> runCatching { ctx.sessionManager.currentCastSession?.isMute = m } }
                CastHost.noteAudio = { codec -> audioNote(codec) }
                ctx.sessionManager.currentCastSession?.let { attach(it) }
            }.addOnFailureListener { Log.w(TAG, "Cast isn't available: ${it.message}") }
        }.onFailure { Log.w(TAG, "Cast isn't available: ${it.message}") }
    }

    private fun client(ctx: CastContext): RemoteMediaClient? = ctx.sessionManager.currentCastSession?.remoteMediaClient

    private fun showDialog(ctx: CastContext) {
        val a = activity
        Log.i(TAG, "Cast button pressed; activity=${a?.javaClass?.simpleName} session=${ctx.sessionManager.currentCastSession != null} selector=${ctx.mergedSelector != null}")
        if (a == null) return
        a.runOnUiThread {
            runCatching {
                if (ctx.sessionManager.currentCastSession != null) MediaRouteControllerDialog(a).show()
                else MediaRouteChooserDialog(a).apply { ctx.mergedSelector?.let { routeSelector = it } }.show()
            }.onFailure { Log.w(TAG, "Cast dialog failed", it) }
        }
    }

    /** Links only this phone can open can't be cast. */
    private fun castable(url: String): Boolean {
        val u = Uri.parse(url)
        val scheme = u.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = u.host?.lowercase() ?: return false
        return host != "localhost" && !host.startsWith("127.") && host != "0.0.0.0" && host != "::1" && host != "10.0.2.2"
    }

    private fun mimeFor(url: String, live: Boolean): String {
        val path = Uri.parse(url).path?.lowercase().orEmpty()
        return when {
            path.endsWith(".m3u8") || "m3u8" in url.lowercase() -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".ts") -> "video/mp2t"
            live -> "video/mp2t"
            else -> "video/mp4"
        }
    }

    /**
     * What the Cast device is told to open. Its standard receiver reads HLS, MP4 and DASH, not a bare transport
     * stream, so a live channel asks the provider for the HLS version of the link (".ts" -> ".m3u8", which Xtream
     * providers serve beside it); YouTube's local manifest is swapped for the video's own single stream.
     */
    private fun castUrlFor(req: CastLoad): String {
        // A Stalker portal's channel: the Cast device is given the portal's own (temporary) stream link.
        if (com.nuvio.app.livetv.data.StalkerLinks.isStalker(req.url)) return com.nuvio.app.livetv.data.StalkerLinks.resolveOffMain(req.url)
        com.nuvio.app.youtube.YouTubeMpdServer.castUrlFor(req.url)?.let { return it }
        // YouTube's picture and sound come from a manifest only this phone holds: share it on the Wi-Fi, for this cast only.
        Regex("""/yt/([^./]+)\.mpd""").find(req.url)?.groupValues?.get(1)?.let { id ->
            com.nuvio.app.youtube.YouTubeMpdServer.manifestBytes(id)?.let { mpd ->
                CastRelay.share(id, mpd, req.headers["User-Agent"])?.let { return it }
            }
        }
        if (req.live) {
            val u = Uri.parse(req.url)
            val path = u.path.orEmpty()
            if (path.endsWith(".ts", ignoreCase = true)) return u.buildUpon().path(path.dropLast(3) + ".m3u8").build().toString()
            if (path.matches(Regex(""".*/live/[^/]+/[^/]+/\d+"""))) return u.buildUpon().path("$path.m3u8").build().toString()
        }
        return req.url
    }

    private fun send(ctx: CastContext, req: CastLoad): Boolean {
        val rmc = client(ctx)
        val a = activity
        if (rmc == null) return false
        val castUrl = castUrlFor(req)
        Log.i(TAG, "Sending: live=${req.live} from=${req.url.substringBefore('?').takeLast(40)} to=${castUrl.substringBefore('?').takeLast(40)} mime=${mimeFor(castUrl, req.live)}")
        if (!castable(castUrl)) {
            a?.runOnUiThread { Toast.makeText(a, "This one only plays on the phone, so it can't be cast.", Toast.LENGTH_LONG).show() }
            return false
        }
        if (req.headers.isNotEmpty()) Log.i(TAG, "Not sent to the Cast device: ${req.headers.keys}")
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, req.title)
            req.subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            req.poster?.takeIf { it.startsWith("http") }?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(castUrl)
            .setStreamType(if (req.live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mimeFor(castUrl, req.live))
            .setMetadata(meta)
            .build()
        rmc.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(info)
                .setAutoplay(true)
                .setCurrentTime(if (req.live) 0L else req.positionMs.coerceAtLeast(0L))
                .build()
        ).setResultCallback { r ->
            val ok = r.status.isSuccess
            Log.i(TAG, "Load ${if (ok) "ok" else "failed: ${r.status.statusMessage ?: r.status.statusCode}"}")
            val current = CastHost.ui.value ?: return@setResultCallback
            CastHost.ui.value = current.copy(message = if (ok) null else "The Cast device couldn't play this one (${r.status.statusMessage ?: "error ${r.status.statusCode}"}).")
        }
        return true
    }

    /** Sound formats the standard receiver often can't play (the picture plays, but silently). */
    private fun audioNote(codec: String?) {
        val c = codec?.lowercase().orEmpty()
        Log.i(TAG, "Stream sound codec: ${codec ?: "unknown"}")
        val risky = listOf("eac3", "e-ac-3", "ec-3", "ac3", "ac-3", "dts", "truehd", "mlp", "opus").any { c.contains(it) } && !c.contains("aac")
        val old = CastHost.ui.value ?: return
        CastHost.ui.value = old.copy(
            note = if (risky) "This stream's sound is $codec, which many Cast devices can't play. If it's silent, try another sound track below, or another source." else null,
        )
    }

    private fun attach(session: CastSession) {
        val name = session.castDevice?.friendlyName ?: "Cast device"
        Log.i(TAG, "Connected to ${session.castDevice?.friendlyName} model=${session.castDevice?.modelName} volume=${session.volume} muted=${session.isMute}")
        CastHost.ui.value = CastUi(deviceName = name)
        val rmc = session.remoteMediaClient ?: return
        rmc.registerCallback(object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() { publish(rmc, name) }
        })
        rmc.addProgressListener({ progress, duration -> publish(rmc, name, progress, duration) }, 1000L)
    }

    private var lastState = -1

    private fun publish(rmc: RemoteMediaClient, name: String, progress: Long = rmc.approximateStreamPosition, duration: Long = rmc.streamDuration) {
        val old = CastHost.ui.value ?: return
        CastHost.lastPositionMs = progress
        val status = rmc.mediaStatus
        val tracks = rmc.mediaInfo?.mediaTracks.orEmpty().filter { it.type == com.google.android.gms.cast.MediaTrack.TYPE_AUDIO }
        val named = tracks.mapIndexed { i, t -> t.id to (t.name?.takeIf { n -> n.isNotBlank() } ?: t.language?.takeIf { l -> l.isNotBlank() } ?: "Track ${i + 1}") }
        val active = status?.activeTrackIds?.toList()?.firstOrNull { id -> tracks.any { it.id == id } }
        val session = context?.sessionManager?.currentCastSession
        if (status != null && status.playerState != lastState) {
            lastState = status.playerState
            Log.i(TAG, "Device state: ${status.playerState} idle=${status.idleReason} streamVolume=${status.streamVolume} streamMuted=${status.isMute}")
        }
        if (named.isNotEmpty() && old.audioTracks != named) Log.i(TAG, "Device sound tracks: " + tracks.joinToString { "${it.id}:${it.name}/${it.language}/${it.contentType}" } + " active=$active")
        CastHost.ui.value = old.copy(
            deviceName = name, playing = rmc.isPlaying, positionMs = progress, durationMs = duration.coerceAtLeast(0L),
            audioTracks = named, activeAudio = active,
            volume = session?.volume?.toFloat() ?: old.volume, muted = session?.isMute ?: old.muted,
        )
    }

    private val sessions = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)
        override fun onSessionEnded(session: CastSession, error: Int) { CastHost.ui.value = null; CastRelay.stop() }
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {
            session.remoteMediaClient?.let { CastHost.lastPositionMs = it.approximateStreamPosition }
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) { CastHost.ui.value = null }
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }
}
