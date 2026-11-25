package org.avmedia.gshockGoogleSync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import org.avmedia.gshockGoogleSync.pairing.PairedWatchState
import org.avmedia.gshockGoogleSync.session.ConnectionButton
import org.avmedia.gshockGoogleSync.session.EntryReason
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.session.SessionPhase
import org.avmedia.gshockGoogleSync.session.SessionState
import javax.inject.Inject
import javax.inject.Singleton

enum class AppScreen {
    INITIAL,
    PRE_CONNECTION,
    CONTENT_SELECTOR,
    MAIN_NAVIGATION,
    RUN_ACTIONS,
    ERROR
}

data class AppNavigationState(
    val screen: AppScreen = AppScreen.INITIAL,
    val errorMessage: String? = null
)

/**
 * The screen to show, derived from the watch [SessionState] and whether a watch is paired.
 * Nothing navigates imperatively: the Session decides where the app is, and this maps it to a screen.
 *
 * With a paired watch the main screens stay up whether or not the watch is connected; the
 * pairing (pre-connection) screen is only for the first setup.
 */
@Singleton
class AppScreenState @Inject constructor(
    sessionManager: SessionManager,
    pairedWatchState: PairedWatchState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val state: StateFlow<AppNavigationState> =
        combine(sessionManager.sessionState, pairedWatchState.hasPairedWatch, ::navigationFor)
            .distinctUntilChanged()
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                navigationFor(sessionManager.sessionState.value, pairedWatchState.hasPairedWatch.value),
            )

    companion object {
        internal fun navigationFor(session: SessionState, hasPairedWatch: Boolean): AppNavigationState {
            val phase = session.phase
            if (hasPairedWatch && phase !is SessionPhase.Ready) {
                val closedWithError = phase is SessionPhase.Closed && phase.isError
                return AppNavigationState(
                    AppScreen.MAIN_NAVIGATION,
                    errorMessage = (phase as? SessionPhase.Closed)?.reason
                        ?.takeIf { closedWithError && it.isNotBlank() },
                )
            }
            return navigationFor(session)
        }

        internal fun navigationFor(session: SessionState): AppNavigationState =
            when (val phase = session.phase) {
                SessionPhase.Disconnected -> AppNavigationState(AppScreen.INITIAL)

                // The pre-connection screen shows the connection progress.
                SessionPhase.Connecting -> AppNavigationState(AppScreen.PRE_CONNECTION)

                is SessionPhase.Ready -> AppNavigationState(
                    when {
                        session.isRunningActions -> AppScreen.RUN_ACTIONS
                        else -> when (phase.entryReason) {
                            EntryReason.NORMAL -> AppScreen.MAIN_NAVIGATION
                            // An always-connected watch connects in the background: open the
                            // main screens directly (there used to be a "hold to unlock" cover).
                            EntryReason.CONTENT_SELECTOR ->
                                if (session.button == ConnectionButton.ALWAYS_CONNECTED) AppScreen.MAIN_NAVIGATION
                                else AppScreen.CONTENT_SELECTOR
                            EntryReason.RUN_ACTIONS -> AppScreen.RUN_ACTIONS
                        }
                    }
                )

                // A connection that ended normally returns to the start screen, which re-checks for
                // paired watches. A failed attempt or an error stays on the pre-connection screen.
                is SessionPhase.Closed -> AppNavigationState(
                    screen = if (!phase.isError && session.button != null) {
                        AppScreen.INITIAL
                    } else {
                        AppScreen.PRE_CONNECTION
                    },
                    errorMessage = phase.reason.takeIf { phase.isError && it.isNotBlank() }
                )
            }
    }
}