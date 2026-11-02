package com.nuvio.app.livetv.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.ExtractorsFactory
import com.nuvio.app.livetv.data.LiveTvRepository
import com.nuvio.app.livetv.model.DrmInfo
import com.nuvio.app.livetv.model.LiveChannel
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LiveTvDrm"

/**
 * Protected channels, as TiviMate and Kodi play them from "#KODIPROP" playlist lines:
 *  - Widevine / PlayReady: the license server (with any headers the playlist gives).
 *  - ClearKey: keys given in the playlist ("kid:key" pairs or a JSON key set), or a ClearKey license URL.
 * Widevine needs a phone that supports it, which nearly all do.
 */
object LiveTvDrm {
    private class Entry(val drm: DrmInfo, val headers: Map<String, String>)

    private val byUrl = ConcurrentHashMap<String, Entry>()

    /** Remembers a channel's protection details, so the player can set them up when it opens the link. */
    fun remember(channel: LiveChannel) {
        val drm = channel.drm
        if (drm == null) byUrl.remove(channel.url) else byUrl[channel.url] = Entry(drm, channel.headers)
    }

    fun hasDrm(url: String): Boolean = byUrl.containsKey(url)

    /** A media source with the channel's DRM set up, or null for a channel that has none (or one that can't be set up). */
    fun mediaSourceFor(
        url: String,
        item: MediaItem,
        dataSourceFactory: DataSource.Factory,
        extractors: ExtractorsFactory,
    ): MediaSource? {
        val entry = byUrl[url] ?: return null
        val drm = entry.drm
        val headers = entry.headers
        return runCatching {
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: LiveTvRepository.DEFAULT_UA)
                .setDefaultRequestProperties(headers.filterKeys { it != "User-Agent" })
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
            val itemBuilder = item.buildUpon()
            if (drm.manifestType == "mpd" || url.lowercase().contains(".mpd")) itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            if (drm.manifestType == "hls") itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            val uuid = when (drm.scheme) {
                "widevine" -> C.WIDEVINE_UUID
                "playready" -> C.PLAYREADY_UUID
                else -> C.CLEARKEY_UUID
            }
            val factory = DefaultMediaSourceFactory(dataSourceFactory, extractors)
            val license = drm.license.trim()
            if (drm.scheme == "clearkey" && !license.startsWith("http", ignoreCase = true)) {
                // Keys in the playlist: answer license requests locally with them.
                val keySet = clearKeyJson(license) ?: return@runCatching null
                factory.setDrmSessionManagerProvider {
                    DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(false)
                        .build(LocalMediaDrmCallback(keySet.toByteArray(Charsets.UTF_8)))
                }
                itemBuilder.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).build())
            } else {
                // License requests go out with the stream's own User-Agent / Referer, plus the playlist's
                // license headers. Without them many license servers refuse the request.
                val licenseHeaders = LinkedHashMap<String, String>()
                headers.forEach { (k, v) ->
                    if (k.equals("User-Agent", true) || k.equals("Referer", true) || k.equals("Origin", true)) licenseHeaders[k] = v
                }
                licenseHeaders.putAll(drm.licenseHeaders)
                factory.setDrmSessionManagerProvider(DefaultDrmSessionManagerProvider().apply { setDrmHttpDataSourceFactory(http) })
                itemBuilder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(uuid)
                        .setLicenseUri(license)
                        .setLicenseRequestHeaders(licenseHeaders)
                        .setMultiSession(true)
                        .build()
                )
            }
            factory.createMediaSource(itemBuilder.build())
        }.onFailure { Log.w(TAG, "Couldn't set up DRM for this stream", it) }.getOrNull()
    }

    /** A ClearKey key set (JSON) from what playlists give: "kid:key" pairs (hex or base64, comma-separated), or JSON as is. */
    internal fun clearKeyJson(raw: String): String? {
        val t = raw.trim()
        if (t.startsWith("{")) return t
        fun b64url(v: String): String {
            val s = v.trim()
            val bytes = if (Regex("^[0-9a-fA-F]+$").matches(s) && s.length % 2 == 0) {
                ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
            } else {
                runCatching { android.util.Base64.decode(s.replace('-', '+').replace('_', '/'), android.util.Base64.DEFAULT) }
                    .getOrElse { return "" }
            }
            return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        }
        val keys = t.split(',').mapNotNull { pair ->
            val kid = pair.substringBefore(':').trim()
            val key = pair.substringAfter(':', "").trim()
            if (kid.isEmpty() || key.isEmpty()) null
            else org.json.JSONObject().put("kty", "oct").put("kid", b64url(kid)).put("k", b64url(key))
        }
        if (keys.isEmpty()) return null
        return org.json.JSONObject().put("keys", org.json.JSONArray(keys)).put("type", "temporary").toString()
    }
}
