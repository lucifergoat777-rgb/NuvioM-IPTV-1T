package com.nuvio.app

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.nuvio.app.core.network.DnsPlatform
import com.nuvio.app.core.network.SharedOkHttpClient
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Custom Application class that implements [SingletonImageLoader.Factory] to guarantee
 * the Coil ImageLoader (with [CustomPosterFallbackInterceptor]) is created before any
 * image request. Without this, Coil's auto-init ContentProvider creates the singleton
 * before MainActivity.onCreate, and interceptors registered later are ignored.
 */
class NuvioApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        com.nuvio.app.cast.CastController.install(this)
        // 1Tube: apply the chosen YouTube content region before the first feed request.
        com.nuvio.app.youtube.YouTubeContentRegion.apply(this)
        // 1Tube: the YouTube page lives in Android-only code and is plugged in here.
        com.nuvio.app.features.youtube.YouTubeHost.playerPanel = { videoId, onDismiss ->
            com.nuvio.app.youtube.YouTubePlayerPanel(videoId, onDismiss)
        }
        com.nuvio.app.features.youtube.YouTubeHost.sponsorSegments = { videoId ->
            com.nuvio.app.youtube.YouTubeSponsorBlock.segments(this, videoId)
        }
        com.nuvio.app.features.youtube.YouTubeHost.sponsorAutoSkip = {
            !com.nuvio.app.youtube.YouTubeSettings.sponsorAsk(this)
        }
        com.nuvio.app.features.youtube.YouTubeHost.playerOverlay = { videoId, position, duration, ended, playNext ->
            com.nuvio.app.youtube.YouTubePlayerOverlay(videoId, position, duration, ended, playNext)
        }
        com.nuvio.app.features.youtube.YouTubeHost.settingsContent = { com.nuvio.app.youtube.YouTubeSettingsContent() }
        com.nuvio.app.features.livetv.LiveTvHost.settingsContent = { com.nuvio.app.livetv.ui.LiveTvSettingsContent() }
        com.nuvio.app.features.livetv.LiveTvHost.channelPanel = { key, onDismiss -> com.nuvio.app.livetv.ui.LiveTvPlayerChannelsPanel(key, onDismiss) }
        com.nuvio.app.livetv.player.LiveTvPauseBuffer.install(this)
        com.nuvio.app.features.livetv.LiveTvHost.askSleep = { com.nuvio.app.livetv.ui.LiveTvSleep.ask() }
        com.nuvio.app.features.livetv.LiveTvHost.playerExtras = { key -> com.nuvio.app.livetv.ui.LiveTvPlayerExtras(key) }
        com.nuvio.app.features.livetv.LiveTvHost.onDemandStreams = { type, tmdb, title, year, season, episode ->
            val live = com.nuvio.app.livetv.LiveTv.get(this)
            val settings = live.prefs.currentSettings()
            if (!settings.onDemandInStreams) emptyList()
            else live.onDemand.sourcesFor(type, tmdb, title, year, season, episode).map { s ->
                com.nuvio.app.features.livetv.OnDemandStream(
                    label = s.label, url = s.url, headers = s.headers,
                    detail = listOfNotNull(s.tech?.quality, s.title.takeIf { it.isNotBlank() }, s.year?.toString()).joinToString("  \u00B7  ").ifBlank { null },
                )
            }
        }
        com.nuvio.app.features.livetv.LiveTvHost.searchSection = { q, play -> com.nuvio.app.livetv.ui.LiveTvSearchSection(q, play) }
        com.nuvio.app.features.livetv.LiveTvHost.homeRow = { play -> com.nuvio.app.livetv.ui.LiveTvHomeRow(play) }
        com.nuvio.app.features.livetv.LiveTvHost.homeEpisodes = { open -> com.nuvio.app.livetv.ui.LiveTvHomeEpisodes(open) }
        com.nuvio.app.features.livetv.LiveTvHost.appOverlay = { com.nuvio.app.livetv.ui.LiveTvSportsHost() }
        com.nuvio.app.features.livetv.LiveTvHost.onDemandReady = { com.nuvio.app.livetv.LiveTv.get(this).onDemandReady.value }
        com.nuvio.app.features.livetv.LiveTvHost.calendarBell = { imdb, name -> com.nuvio.app.livetv.ui.rememberLiveTvBell(imdb, name) }
        com.nuvio.app.features.livetv.LiveTvHost.zap = { key, delta -> com.nuvio.app.livetv.ui.zapLiveTv(this, key, delta) }
        com.nuvio.app.features.livetv.LiveTvHost.nowPlaying = { key -> com.nuvio.app.livetv.ui.liveTvNowPlaying(key) }
        com.nuvio.app.features.livetv.LiveTvHost.screen = { modifier, onPlay ->
            com.nuvio.app.livetv.ui.LiveTvScreen(modifier, onPlay)
        }
        com.nuvio.app.features.youtube.YouTubeHost.recordProgress = { contentId, position, duration ->
            if (contentId.startsWith("livetv:recording:")) {
                // Where you got to in a recording, so it starts from there next time.
                com.nuvio.app.livetv.record.RecordingResume.save(
                    this, com.nuvio.app.livetv.record.RecordingResume.contentIdFor(contentId.removePrefix("livetv:recording:")),
                    position, duration,
                )
            } else if (contentId.startsWith("livetv:vod:")) {
                com.nuvio.app.livetv.ondemand.VodResume.save(this, contentId, position, duration)
            } else if (!contentId.startsWith("livetv:")) {
                com.nuvio.app.youtube.YouTubeProgress.record(this, contentId, position, duration)
            }
        }
        com.nuvio.app.features.youtube.YouTubeHost.playerControls = { videoId, openPanel ->
            com.nuvio.app.youtube.rememberYouTubePlayerAction(videoId, openPanel)
        }
        com.nuvio.app.features.youtube.YouTubeHost.screen = { modifier, onPlay ->
            com.nuvio.app.youtube.YouTubeScreen(modifier, onPlay)
        }
        DnsPlatform.initialize(this)
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache {
                coil3.memory.MemoryCache.Builder().maxSizePercent(context, 0.15).build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .components {
                add(CustomPosterFallbackInterceptor())
                add(SvgDecoder.Factory())
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = {
                            HttpClient(OkHttp) { engine { preconfigured = SharedOkHttpClient.base } }
                        },
                        cacheStrategy = { CacheControlCacheStrategy() },
                    ),
                )
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
