package com.flykpicks.android.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionEvent {
    /**
     * The network layer cleared the session (tokens revoked or refresh rejected). The UI should
     * return to Login and show [userMessage]. Device trust is kept.
     */
    data class Ended(val reason: Reason) : SessionEvent {
        val userMessage: String get() = "Your session expired. Please sign in again."
    }

    enum class Reason { TOKEN_REVOKED, REFRESH_REJECTED }
}

/** Signals from the network layer to the app shell (iOS `onAuthFailure`). */
@Singleton
class SessionEvents @Inject constructor() {
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    fun emit(event: SessionEvent) {
        _events.tryEmit(event)
    }
}
