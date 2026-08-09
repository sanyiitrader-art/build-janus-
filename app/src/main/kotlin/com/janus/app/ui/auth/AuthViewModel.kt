package com.janus.app.ui.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for AuthScreen.
 *
 * Phase 1 scope: exposes just enough state (idle/inProgress/error) for the
 * screen to show a loading/disabled state once real sign-in is wired up.
 * Not yet connected to AuthScreen.kt's composable — AuthScreen currently
 * calls its onSkip/onAuthenticated callbacks directly from JanusNavGraph
 * without going through this ViewModel, since there is no real credential
 * flow yet to drive AuthUiState transitions.
 *
 * This ViewModel becomes load-bearing once AuthRepository / GoogleAuthProvider
 * exist (dedicated auth subsystem, isolated from the remote-control code per
 * spec #4) — at that point AuthScreen will collect `uiState` and call
 * `signInWithGoogle()` / `skip()` on this ViewModel instead of invoking
 * NavGraph callbacks directly.
 */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object InProgress : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun resetError() {
        _uiState.value = AuthUiState.Idle
    }
}