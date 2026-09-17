package com.nexa.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.core.network.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    val sessionStatus = authRepository.sessionStatus

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun signIn(email: String, password: String) = runAuth {
        authRepository.signInWithEmail(email.trim(), password)
        "Signed in to NEXA."
    }

    fun signUp(email: String, password: String, confirmPassword: String) = runAuth {
        require(password == confirmPassword) { "Passwords do not match." }
        require(password.length >= 6) { "Password must be at least 6 characters." }
        authRepository.signUpWithEmail(email.trim(), password)
        "Account created. Check your email if verification is required."
    }

    fun sendPasswordReset(email: String) = runAuth {
        authRepository.sendPasswordResetEmail(email.trim())
        "Password reset email sent. Check your inbox."
    }

    fun updatePassword(password: String, confirmPassword: String) = runAuth {
        require(password == confirmPassword) { "Passwords do not match." }
        require(password.length >= 6) { "Password must be at least 6 characters." }
        authRepository.updatePassword(password)
        "Password updated successfully."
    }

    fun signOut() = viewModelScope.launch {
        _busy.value = true
        try {
            authRepository.signOut()
        } catch (e: Exception) {
            _message.value = e.message ?: "Could not sign out."
        } finally {
            _busy.value = false
        }
    }

    private fun runAuth(block: suspend () -> String) = viewModelScope.launch {
        _busy.value = true
        _message.value = null
        try {
            _message.value = block()
        } catch (e: Exception) {
            _message.value = e.message ?: "Something went wrong. Please try again."
        } finally {
            _busy.value = false
        }
    }
}
