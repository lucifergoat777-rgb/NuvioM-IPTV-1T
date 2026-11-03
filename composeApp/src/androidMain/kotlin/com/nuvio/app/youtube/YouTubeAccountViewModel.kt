package com.nuvio.app.youtube

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.liskovsoft.mediaserviceinterfaces.oauth.Account
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One account or channel profile you can switch to. */
data class YtAccount(
    val id: Int,
    val name: String,
    val detail: String?,
    val avatar: String?,
    val selected: Boolean
)

data class YouTubeAccountState(
    val signedIn: Boolean = false,
    /** False until the first look at the account has finished, so "Sign in" isn't shown while it's still loading. */
    val checked: Boolean = false,
    /** The account or profile in use. */
    val accountName: String? = null,
    /** Every account and channel profile on this device. */
    val accounts: List<YtAccount> = emptyList(),
    /** The code to enter at [url] while a sign-in is waiting. */
    val code: String? = null,
    val url: String = "youtube.com/activate",
    val error: String? = null
) {
    /** What a phone should open to enter the code; the code is filled in when YouTube allows it. */
    val activateLink: String? get() = code?.let { "https://www.$url?user_code=$it" }
}

/** Signs in to YouTube with the TV device-code flow, switches between accounts and profiles, and signs out. */
class YouTubeAccountViewModel (private val context: Context) : ViewModel() {
    private companion object {
        const val TAG = "YouTubeAccount"
        const val RECHECKS = 2
        const val RECHECK_DELAY_MS = 700L
    }

    private val _state = MutableStateFlow(YouTubeAccountState())
    val state: StateFlow<YouTubeAccountState> = _state.asStateFlow()

    private var signIn: Disposable? = null
    private var rawAccounts: List<Account> = emptyList()

    private val signInService by lazy {
        GlobalPreferences.instance(context)
        YouTubeServiceManager.instance().signInService
    }

    private fun label(a: Account): String =
        a.name?.takeIf(String::isNotBlank) ?: a.email?.takeIf(String::isNotBlank) ?: "Account"

    fun refresh() = check(attempt = 0)

    /**
     * Right after the app starts, YouTube's sign-in data can still be loading and report "not signed in" for a
     * moment. So a "no" is looked at again before it is believed.
     */
    private fun check(attempt: Int) {
        // Reading accounts touches storage, so keep it off the main thread.
        Schedulers.io().scheduleDirect {
            val signed = runCatching { signInService.isSigned }.getOrDefault(false)
            if (!signed && attempt < RECHECKS) {
                Schedulers.io().scheduleDirect({ check(attempt + 1) }, RECHECK_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                return@scheduleDirect
            }
            val list = runCatching { signInService.accounts.orEmpty() }.getOrDefault(emptyList())
            rawAccounts = list
            val items = list.map {
                YtAccount(
                    id = it.id,
                    name = label(it),
                    // Only worth showing the email when the name is something else.
                    detail = it.email?.takeIf { e -> e.isNotBlank() && e != label(it) },
                    avatar = it.avatarImageUrl,
                    selected = it.isSelected
                )
            }
            val current = runCatching { signInService.selectedAccount }.getOrNull()
            _state.update { it.copy(signedIn = signed, checked = true, accountName = current?.let(::label), accounts = items) }
        }
    }

    fun startSignIn() {
        signIn?.dispose()
        _state.update { it.copy(error = null, code = null) }
        signIn = signInService.signInObserve()
            .subscribeOn(Schedulers.io())
            .subscribe(
                { code -> _state.update { it.copy(code = code) } },
                { e ->
                    Log.e(TAG, "sign-in failed", e)
                    YouTubeSignInReturn.cancelled()
                    _state.update { it.copy(code = null, error = e.message ?: "Couldn't start sign-in") }
                },
                {
                    // Approved on the other device.
                    _state.update { it.copy(code = null) }
                    refresh()
                    YouTubeSignInReturn.backToApp(context)
                }
            )
    }

    fun cancelSignIn() {
        YouTubeSignInReturn.cancelled()
        signIn?.dispose()
        _state.update { it.copy(code = null) }
    }

    /** Switches to another account or profile. [onDone] runs once it has taken effect. */
    fun selectAccount(id: Int, onDone: () -> Unit = {}) {
        Schedulers.io().scheduleDirect {
            runCatching { rawAccounts.firstOrNull { it.id == id }?.let(signInService::selectAccount) }
                .onFailure { Log.e(TAG, "couldn't switch account", it) }
            // The feeds and stream lookups belong to the account that was in use.
            runCatching { YouTubeServiceManager.instance().invalidateCache() }
            refresh()
            onDone()
        }
    }

    fun signOut(onDone: () -> Unit = {}) {
        Schedulers.io().scheduleDirect {
            runCatching { signInService.selectedAccount?.let(signInService::removeAccount) }
                .onFailure { Log.e(TAG, "sign-out failed", it) }
            runCatching { YouTubeServiceManager.instance().invalidateCache() }
            refresh()
            onDone()
        }
    }

    override fun onCleared() {
        signIn?.dispose()
    }
}
