package com.nuvio.app.features.simkl

import co.touchlab.kermit.Logger
import com.nuvio.app.features.tracking.TrackingAuthProvider
import com.nuvio.app.features.tracking.TrackingCapability
import com.nuvio.app.features.tracking.TrackingProviderDescriptor
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SimklAuthRepository : TrackingAuthProvider {
    private val log = Logger.withTag("SimklAuth")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val authorizationMutex = Mutex()

    private val _uiState = MutableStateFlow(SimklAuthUiState())
    val uiState: StateFlow<SimklAuthUiState> = _uiState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.SIMKL,
        displayName = "Simkl",
        capabilities = setOf(
            TrackingCapability.AUTHENTICATION,
            TrackingCapability.LIBRARY_READ,
            TrackingCapability.LIBRARY_WRITE,
            TrackingCapability.WATCHED_READ,
            TrackingCapability.WATCHED_WRITE,
            TrackingCapability.PROGRESS_READ,
            TrackingCapability.PROGRESS_WRITE,
            TrackingCapability.SCROBBLE,
        ),
    )

    private var hasLoaded = false
    private var storedState = SimklStoredAuthState()
    private var accessToken: String? = null

    init {
        TrackingProviderRegistry.register(this)
    }

    override fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    override fun onProfileChanged() {
        loadFromDisk()
    }

    override fun clearLocalState() {
        hasLoaded = false
        storedState = SimklStoredAuthState()
        accessToken = null
        publish()
    }

    override fun removeStoredProfile(profileId: Int) {
        SimklAuthStorage.removeProfile(profileId)
    }

    fun snapshot(): SimklAuthUiState {
        ensureLoaded()
        return uiState.value
    }

    fun hasRequiredCredentials(): Boolean = SimklConfig.CLIENT_ID.isNotBlank()

    /**
     * Starts signing in. Simkl's client IDs are now "OAuth 2" apps that sign in with a code: this asks
     * for one, returns the Simkl page to open (with the code already filled in), and waits in the
     * background for it to be approved there.
     */
    suspend fun onConnectRequested(): String? {
        ensureLoaded()
        if (!hasRequiredCredentials()) {
            publish(error = SimklAuthError.MISSING_CLIENT_ID)
            return null
        }
        publish(isLoading = true, error = null)
        val response = try {
            SimklApi.client.execute(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = "/oauth2/device",
                    body = json.encodeToString(SimklDeviceRequest(clientId = SimklConfig.CLIENT_ID)),
                    requiresAuthentication = false,
                    retryPolicy = SimklRetryPolicy.NEVER,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w { "Simkl device code request failed: ${error.message}" }
            publish(isLoading = false, error = SimklAuthError.TOKEN_EXCHANGE_FAILED)
            return null
        }
        val device = runCatching { json.decodeFromString<SimklDeviceResponse>(response.body) }.getOrNull()
            ?.takeIf { it.deviceCode.isNotBlank() && it.userCode.isNotBlank() }
        if (device == null) {
            publish(isLoading = false, error = SimklAuthError.INVALID_TOKEN_RESPONSE)
            return null
        }
        val page = device.verificationUriComplete?.takeIf(String::isNotBlank)
            ?: device.verificationUri?.takeIf(String::isNotBlank)
            ?: "https://simkl.com/pin"
        SimklAuthStorage.saveCodeVerifier(device.deviceCode)
        storedState = storedState.copy(
            pendingAuthorizationState = device.userCode,
            pendingAuthorizationStartedAtEpochMs = SimklPlatformClock.nowEpochMs(),
            pendingVerificationUrl = page,
            pendingPollIntervalSec = device.interval,
        )
        persistMetadata()
        publish(isLoading = false, error = null)
        startPolling()
        return page
    }

    private var pollingJob: kotlinx.coroutines.Job? = null

    /** Waits for the code to be approved on Simkl, then signs in. */
    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (true) {
                val deviceCode = SimklAuthStorage.loadCodeVerifier()?.takeIf(String::isNotBlank) ?: return@launch
                if (isSimklAuthorizationExpired(storedState.pendingAuthorizationStartedAtEpochMs, SimklPlatformClock.nowEpochMs())) {
                    clearPendingAuthorization()
                    persistMetadata()
                    publish(error = SimklAuthError.AUTHORIZATION_EXPIRED)
                    return@launch
                }
                kotlinx.coroutines.delay((storedState.pendingPollIntervalSec ?: 5).coerceAtLeast(3) * 1_000L)
                if (SimklAuthStorage.loadCodeVerifier().isNullOrBlank()) return@launch
                val response = try {
                    SimklApi.client.execute(
                        SimklApiRequest(
                            method = SimklHttpMethod.POST,
                            path = "/oauth2/token",
                            body = json.encodeToString(
                                SimklDeviceTokenRequest(clientId = SimklConfig.CLIENT_ID, deviceCode = deviceCode),
                            ),
                            requiresAuthentication = false,
                            retryPolicy = SimklRetryPolicy.NEVER,
                        ),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: SimklApiException) {
                    when (error.errorCode) {
                        "authorization_pending", "slow_down", "transport_failure", null -> continue
                        else -> {
                            log.w { "Simkl sign-in ended: ${error.errorCode}" }
                            clearPendingAuthorization()
                            persistMetadata()
                            publish(error = SimklAuthError.AUTHORIZATION_REVOKED)
                            return@launch
                        }
                    }
                } catch (error: Throwable) {
                    continue
                }
                val token = runCatching { json.decodeFromString<SimklTokenResponse>(response.body) }.getOrNull()
                    ?.takeIf { it.accessToken.isNotBlank() }
                    ?: continue
                authorizationMutex.withLock { finishSignIn(token) }
                return@launch
            }
        }
    }

    private suspend fun finishSignIn(token: SimklTokenResponse) {
        accessToken = token.accessToken
        SimklAuthStorage.saveAccessToken(token.accessToken)
        SimklAuthStorage.saveRefreshToken(token.refreshToken)
        clearPendingAuthorization()
        storedState = storedState.copy(tokenExpiresAtEpochMs = tokenExpiry(token))
        persistMetadata()
        publish(isLoading = false, error = null)
        fetchAndStoreUserSettings()
        SimklSyncRepository.refreshAsync(
            intent = TrackingRefreshIntent.INVALIDATED,
            origin = SimklRefreshOrigin.AUTHORIZATION,
        )
    }

    /** OAuth 2 sign-ins last 7 days (and come with a refresh token to renew them). */
    private fun tokenExpiry(token: SimklTokenResponse): Long =
        SimklPlatformClock.nowEpochMs() + (token.expiresIn?.takeIf { it > 0L } ?: DEFAULT_TOKEN_SECONDS) * 1_000L

    private var refreshing = false

    /** Swaps the refresh token for a new access token, a day before the old one runs out. */
    private fun refreshTokenInBackground() {
        if (refreshing) return
        val refresh = SimklAuthStorage.loadRefreshToken()?.takeIf(String::isNotBlank) ?: return
        refreshing = true
        scope.launch {
            try {
                val response = SimklApi.client.execute(
                    SimklApiRequest(
                        method = SimklHttpMethod.POST,
                        path = "/oauth2/token",
                        body = json.encodeToString(SimklRefreshRequest(clientId = SimklConfig.CLIENT_ID, refreshToken = refresh)),
                        requiresAuthentication = false,
                        retryPolicy = SimklRetryPolicy.NEVER,
                    ),
                )
                val token = runCatching { json.decodeFromString<SimklTokenResponse>(response.body) }.getOrNull()
                    ?.takeIf { it.accessToken.isNotBlank() }
                if (token != null) {
                    accessToken = token.accessToken
                    SimklAuthStorage.saveAccessToken(token.accessToken)
                    token.refreshToken?.takeIf(String::isNotBlank)?.let(SimklAuthStorage::saveRefreshToken)
                    storedState = storedState.copy(tokenExpiresAtEpochMs = tokenExpiry(token))
                    persistMetadata()
                    publish(error = null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w { "Simkl token refresh failed: ${error.message}" }
            } finally {
                refreshing = false
            }
        }
    }

    fun pendingAuthorizationUrl(): String? {
        ensureLoaded()
        val state = storedState.pendingAuthorizationState?.takeIf(String::isNotBlank) ?: return null
        val verifier = SimklAuthStorage.loadCodeVerifier()?.takeIf(String::isNotBlank) ?: run {
            clearPendingAuthorization()
            persistMetadata()
            publish(error = SimklAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        if (isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = SimklPlatformClock.nowEpochMs(),
            )
        ) {
            clearPendingAuthorization()
            persistMetadata()
            publish(error = SimklAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        storedState.pendingVerificationUrl?.takeIf(String::isNotBlank)?.let {
            startPolling()
            return it
        }
        return authorizationUrl(
            SimklPkceMaterial(
                verifier = verifier,
                challenge = SimklPkceCrypto.sha256(verifier.encodeToByteArray()).base64UrlWithoutPadding(),
                state = state,
            ),
        )
    }

    fun onCancelAuthorization() {
        ensureLoaded()
        clearPendingAuthorization()
        persistMetadata()
        publish(error = null)
    }

    override fun handleAuthCallback(url: String): Boolean {
        ensureLoaded()
        return when (val callback = parseSimklAuthCallback(url, SimklConfig.REDIRECT_URI)) {
            SimklAuthCallback.NotSimkl -> false
            SimklAuthCallback.Invalid -> {
                clearPendingAuthorization()
                persistMetadata()
                publish(error = SimklAuthError.INVALID_CALLBACK)
                true
            }
            is SimklAuthCallback.AuthorizationCode -> {
                scope.launch { completeAuthorization(callback) }
                true
            }
        }
    }

    fun onDisconnectRequested() {
        ensureLoaded()
        accessToken = null
        SimklAuthStorage.saveAccessToken(null)
        SimklAuthStorage.saveRefreshToken(null)
        clearPendingAuthorization()
        storedState = SimklStoredAuthState()
        persistMetadata()
        SimklSyncRepository.clearLocalState()
        publish(error = null)
    }

    internal fun authorizedAccessToken(): String? {
        ensureLoaded()
        val token = accessToken?.takeIf(String::isNotBlank) ?: return null
        val expiresAt = storedState.tokenExpiresAtEpochMs
        val hasRefresh = !SimklAuthStorage.loadRefreshToken().isNullOrBlank()
        if (expiresAt != null && hasRefresh && SimklPlatformClock.nowEpochMs() >= expiresAt - REFRESH_EARLY_MS) {
            refreshTokenInBackground()
        }
        if (expiresAt != null && SimklPlatformClock.nowEpochMs() >= expiresAt - TOKEN_EXPIRY_SKEW_MS) {
            if (hasRefresh) return null
            invalidateCredentials(SimklAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        return token
    }

    internal fun onUnauthorizedResponse() {
        invalidateCredentials(SimklAuthError.AUTHORIZATION_REVOKED)
    }

    suspend fun refreshUserSettings(): String? {
        authorizedAccessToken() ?: return null
        return if (fetchAndStoreUserSettings()) storedState.username else null
    }

    internal suspend fun synchronizeUserSettings(activityWatermark: String?) {
        authorizedAccessToken() ?: return
        when (simklSettingsRefreshAction(storedState, activityWatermark)) {
            SimklSettingsRefreshAction.NONE -> Unit
            SimklSettingsRefreshAction.RECORD_WATERMARK -> {
                storedState = storedState.copy(settingsActivityWatermark = activityWatermark)
                persistMetadata()
            }
            SimklSettingsRefreshAction.FETCH -> {
                fetchAndStoreUserSettings(activityWatermark)
            }
        }
    }

    private suspend fun completeAuthorization(callback: SimklAuthCallback.AuthorizationCode) =
        authorizationMutex.withLock {
            publish(isLoading = true, error = null)
            val expectedState = storedState.pendingAuthorizationState
            val verifier = SimklAuthStorage.loadCodeVerifier()
            val isExpired = isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = SimklPlatformClock.nowEpochMs(),
            )
            if (expectedState.isNullOrBlank() || verifier.isNullOrBlank() || isExpired) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.AUTHORIZATION_EXPIRED)
                return@withLock
            }
            if (!constantTimeEquals(callback.state, expectedState)) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.INVALID_CALLBACK_STATE)
                return@withLock
            }

            val request = SimklTokenRequest(
                code = callback.code,
                clientId = SimklConfig.CLIENT_ID,
                codeVerifier = verifier,
                redirectUri = SimklConfig.REDIRECT_URI,
            )
            val response = try {
                SimklApi.client.execute(
                    SimklApiRequest(
                        method = SimklHttpMethod.POST,
                        path = "/oauth/token",
                        body = json.encodeToString(request),
                        requiresAuthentication = false,
                        retryPolicy = SimklRetryPolicy.NEVER,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w { "Simkl token exchange failed: ${error.message}" }
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.TOKEN_EXCHANGE_FAILED)
                return@withLock
            }
            val token = runCatching { json.decodeFromString<SimklTokenResponse>(response.body) }
                .getOrNull()
                ?.takeIf { it.accessToken.isNotBlank() }
            if (token == null) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.INVALID_TOKEN_RESPONSE)
                return@withLock
            }

            accessToken = token.accessToken
            SimklAuthStorage.saveAccessToken(token.accessToken)
            clearPendingAuthorization()
            storedState = storedState.copy(
                tokenExpiresAtEpochMs = token.expiresIn
                    ?.takeIf { seconds -> seconds > 0L }
                    ?.let { seconds -> SimklPlatformClock.nowEpochMs() + seconds * 1_000L },
            )
            persistMetadata()
            publish(isLoading = false, error = null)
            fetchAndStoreUserSettings()
            SimklSyncRepository.refreshAsync(
                intent = TrackingRefreshIntent.INVALIDATED,
                origin = SimklRefreshOrigin.AUTHORIZATION,
            )
        }

    private suspend fun fetchAndStoreUserSettings(activityWatermark: String? = null): Boolean {
        val response = try {
            SimklApi.client.execute(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = "/users/settings",
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w { "Failed to fetch Simkl user settings: ${error.message}" }
            return false
        }
        val settings = runCatching { json.decodeFromString<SimklUserSettingsResponse>(response.body) }
            .getOrNull() ?: return false
        storedState = storedState.copy(
            username = settings.user?.name,
            accountId = settings.account?.id,
            hasFetchedUserSettings = true,
            settingsActivityWatermark = activityWatermark ?: storedState.settingsActivityWatermark,
        )
        persistMetadata()
        publish(error = null)
        return true
    }

    private fun loadFromDisk() {
        hasLoaded = true
        storedState = SimklAuthStorage.loadMetadataPayload()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { payload ->
                runCatching { json.decodeFromString<SimklStoredAuthState>(payload) }
                    .onFailure { error -> log.w { "Failed to parse Simkl auth metadata: ${error.message}" } }
                    .getOrNull()
            }
            ?: SimklStoredAuthState()
        accessToken = SimklAuthStorage.loadAccessToken()?.takeIf(String::isNotBlank)
        if (accessToken != null && SimklAuthStorage.loadRefreshToken().isNullOrBlank() && storedState.tokenExpiresAtEpochMs?.let { expiresAt ->
                SimklPlatformClock.nowEpochMs() >= expiresAt - TOKEN_EXPIRY_SKEW_MS
            } == true
        ) {
            accessToken = null
            SimklAuthStorage.saveAccessToken(null)
            storedState = SimklStoredAuthState()
            persistMetadata()
        }
        if (storedState.hasPendingAuthorization && isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = SimklPlatformClock.nowEpochMs(),
            )
        ) {
            clearPendingAuthorization()
            persistMetadata()
        }
        publish(error = null)
        // Waiting for a code to be approved when the app was closed: carry on waiting.
        if (storedState.hasPendingAuthorization && !storedState.pendingVerificationUrl.isNullOrBlank()) startPolling()
        // A sign-in that's about to run out: renew it.
        val expiresAt = storedState.tokenExpiresAtEpochMs
        if (accessToken != null && expiresAt != null && SimklPlatformClock.nowEpochMs() >= expiresAt - REFRESH_EARLY_MS) refreshTokenInBackground()
    }

    private fun invalidateCredentials(error: SimklAuthError) {
        accessToken = null
        SimklAuthStorage.saveAccessToken(null)
        SimklAuthStorage.saveRefreshToken(null)
        clearPendingAuthorization()
        storedState = SimklStoredAuthState()
        persistMetadata()
        SimklSyncRepository.clearLocalState()
        publish(isLoading = false, error = error)
    }

    private fun clearPendingAuthorization() {
        SimklAuthStorage.saveCodeVerifier(null)
        storedState = storedState.copy(
            pendingAuthorizationState = null,
            pendingAuthorizationStartedAtEpochMs = null,
            pendingVerificationUrl = null,
            pendingPollIntervalSec = null,
        )
    }

    private fun persistMetadata() {
        SimklAuthStorage.saveMetadataPayload(json.encodeToString(storedState))
    }

    private fun publish(
        isLoading: Boolean = _uiState.value.isLoading,
        error: SimklAuthError? = _uiState.value.error,
    ) {
        val authenticated = !accessToken.isNullOrBlank()
        _isAuthenticated.value = authenticated
        _uiState.value = SimklAuthUiState(
            mode = when {
                authenticated -> SimklConnectionMode.CONNECTED
                storedState.hasPendingAuthorization -> SimklConnectionMode.AWAITING_APPROVAL
                else -> SimklConnectionMode.DISCONNECTED
            },
            credentialsConfigured = hasRequiredCredentials(),
            isLoading = isLoading,
            username = storedState.username,
            accountId = storedState.accountId,
            tokenExpiresAtEpochMs = storedState.tokenExpiresAtEpochMs,
            pendingAuthorizationStartedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
            error = error,
        )
    }

    private fun authorizationUrl(material: SimklPkceMaterial): String =
        buildSimklAuthorizationUrl(
            clientId = SimklConfig.CLIENT_ID,
            redirectUri = SimklConfig.REDIRECT_URI,
            appName = SimklConfig.APP_NAME,
            appVersion = simklAppVersion,
            material = material,
        )

    private const val TOKEN_EXPIRY_SKEW_MS = 60_000L
    private const val REFRESH_EARLY_MS = 24L * 60L * 60L * 1_000L
    private const val DEFAULT_TOKEN_SECONDS = 7L * 24L * 60L * 60L
}

@Serializable
private data class SimklTokenRequest(
    val code: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("code_verifier") val codeVerifier: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("grant_type") val grantType: String = "authorization_code",
)

@Serializable
private data class SimklTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
private data class SimklDeviceRequest(
    @SerialName("client_id") val clientId: String,
    val scope: String = "media:read media:write",
)

@Serializable
private data class SimklDeviceResponse(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val interval: Int? = null,
)

@Serializable
private data class SimklDeviceTokenRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("device_code") val deviceCode: String,
    @SerialName("grant_type") val grantType: String = "urn:ietf:params:oauth:grant-type:device_code",
)

@Serializable
private data class SimklRefreshRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("grant_type") val grantType: String = "refresh_token",
)

@Serializable
private data class SimklUserSettingsResponse(
    val user: SimklUser? = null,
    val account: SimklAccount? = null,
)

@Serializable
private data class SimklUser(
    val name: String? = null,
)

@Serializable
private data class SimklAccount(
    val id: Long? = null,
)
