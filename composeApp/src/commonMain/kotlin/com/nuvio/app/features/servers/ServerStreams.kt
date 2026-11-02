package com.nuvio.app.features.servers

import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.compose.resources.getString

internal class ServerStreamSource(
    val addonId: String,
    val addonName: String,
    val preferred: Boolean,
    private val loader: suspend () -> List<StreamItem>,
) {
    fun loadingGroup(): AddonStreamGroup =
        AddonStreamGroup(addonName = addonName, addonId = addonId, streams = emptyList(), isLoading = true)

    suspend fun load(): AddonStreamGroup = try {
        AddonStreamGroup(addonName = addonName, addonId = addonId, streams = loader(), isLoading = false)
    } catch (error: Throwable) {
        if (error is CancellationException) currentCoroutineContext().ensureActive()
        AddonStreamGroup(
            addonName = addonName,
            addonId = addonId,
            streams = emptyList(),
            isLoading = false,
            error = getString(error.serverFailure().message()),
        )
    }
}

internal object ServerStreams {
    fun isNativeRequest(videoId: String): Boolean = ServerItemRef.isServerId(videoId)

    fun canServe(type: String, videoId: String): Boolean {
        ServerItemRef.parse(videoId)?.let { ref -> return ServerRepository.connection(ref.connectionId)?.enabled == true }
        val connections = ServerRepository.enabledConnections().ifEmpty { return false }
        val request = ServerMatcher.shared.request(type, videoId, season = null, episode = null) ?: return false
        return connections.any { ServerMatcher.shared.supports(it, request.kind) }
    }

    fun isServerSourceId(addonId: String?): Boolean = addonId?.startsWith(GROUP_PREFIX) == true

    fun sources(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?,
        forceRefresh: Boolean = false,
    ): List<ServerStreamSource> {
        ServerItemRef.parse(videoId)?.let { ref ->
            val connection = ServerRepository.connection(ref.connectionId) ?: return emptyList()
            return listOf(source(connection, preferred = true) { candidates(ref) })
        }
        val onDemand = onDemandSource(type, videoId, season, episode)
        val connections = ServerRepository.enabledConnections().ifEmpty { return listOfNotNull(onDemand) }
        val request = ServerMatcher.shared.request(type, videoId, season, episode) ?: return listOfNotNull(onDemand)
        return connections
            .filter { ServerMatcher.shared.supports(it, request.kind) }
            .map { connection ->
                source(connection, preferred = connection.useCatalogMetadata) {
                    ServerMatcher.shared.match(connection, request, forceRefresh).flatMap { candidates(it) }
                }
            } + listOfNotNull(onDemand)
    }

    /** "Watch On Demand": your Xtream providers' copies of this movie or episode, when they have it. */
    private fun onDemandSource(type: String, videoId: String, season: Int?, episode: Int?): ServerStreamSource? {
        val find = com.nuvio.app.features.livetv.LiveTvHost.onDemandStreams ?: return null
        val request = ServerMatcher.shared.request(type, videoId, season, episode) ?: return null
        val title = request.name ?: return null
        return ServerStreamSource(addonId = ON_DEMAND_ID, addonName = "On Demand", preferred = false) {
            find(type, request.ids.tmdb?.toString(), title, request.year, request.season, request.episode).map { s ->
                StreamItem(
                    name = s.label,
                    description = s.detail,
                    url = s.url,
                    sourceName = "On Demand",
                    addonName = "On Demand",
                    addonId = ON_DEMAND_ID,
                    behaviorHints = StreamBehaviorHints(proxyHeaders = s.headers.takeIf { it.isNotEmpty() }?.let { h -> com.nuvio.app.features.streams.StreamProxyHeaders(request = h) }),
                )
            }
        }
    }

    private fun source(
        connection: ServerConnection,
        preferred: Boolean,
        loader: suspend () -> List<StreamItem>,
    ): ServerStreamSource =
        ServerStreamSource(
            addonId = groupId(connection.id),
            addonName = ServerRepository.sourceLabel(connection),
            preferred = preferred,
            loader = loader,
        )

    suspend fun candidates(ref: ServerItemRef): List<StreamItem> {
        val connection = ServerRepository.connection(ref.connectionId) ?: throw ServerException(ServerFailure.NOT_FOUND)
        val label = ServerRepository.sourceLabel(connection)
        return ServerRepository.call(ref.connectionId) { provider, session -> provider.candidates(session, ref.itemId) }
            .map { candidate ->
                StreamItem(
                    name = candidate.title,
                    description = candidate.description,
                    sourceName = label,
                    addonName = label,
                    addonId = groupId(connection.id),
                    behaviorHints = StreamBehaviorHints(
                        filename = candidate.filename,
                        videoSize = candidate.sizeBytes,
                    ),
                    serverTarget = candidate.target,
                )
            }
    }

    private fun groupId(connectionId: String) = "$GROUP_PREFIX$connectionId"

    private const val GROUP_PREFIX = "server:"
    private const val ON_DEMAND_ID = "ondemand"
}
