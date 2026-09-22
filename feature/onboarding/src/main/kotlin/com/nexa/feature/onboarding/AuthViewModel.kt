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
class AuthViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {
    val sessionStatus = authRepository.sessionStatus
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun saveOnboardingProfile(displayName: String, assistantName: String, onComplete: () -> Unit) = viewModelScope.launch {
        _busy.value = true; _message.value = null
        try {
            require(displayName.trim().length >= 2) { "Enter your name." }
            require(assistantName.trim().isNotEmpty()) { "Give your PA a name." }
            authRepository.saveOnboardingProfile(displayName.trim(), assistantName.trim())
            onComplete()
        } catch (e: Exception) { _message.value = e.message ?: "Could not save your profile." }
        finally { _busy.value = false }
    }

    suspend fun isOnboardingComplete(): Boolean = authRepository.isOnboardingComplete()
    suspend fun hasActiveSubscription(): Boolean = authRepository.subscriptionAccess()
    suspend fun initiateSubscription(plan: String): AuthRepository.RemitaInitResult = authRepository.initiateSubscription(plan)

    fun signIn(email: String, password: String, onSignedIn: () -> Unit) = viewModelScope.launch {
        _busy.value = true; _message.value = null
        try {
            val clean = email.trim()
            require(clean.contains("@") && clean.contains(".")) { "Enter a valid Gmail address." }
            require(password.length >= 6) { "Enter your password." }
            authRepository.signInWithPassword(clean, password)
            onSignedIn()
        } catch (e: Exception) { _message.value = friendlyError(e) }
        finally { _busy.value = false }
    }

    fun createAccount(email: String, password: String, confirmPassword: String, onCreated: () -> Unit) = viewModelScope.launch {
        _busy.value = true; _message.value = null
        try {
            val clean = email.trim()
            require(clean.contains("@") && clean.contains(".")) { "Enter a valid Gmail address." }
            require(password == confirmPassword) { "Passwords do not match." }
            require(password.length >= 6) { "Password must be at least 6 characters." }
            authRepository.createAccount(clean, password)
            onCreated()
        } catch (e: Exception) { _message.value = friendlyError(e) }
        finally { _busy.value = false }
    }

    fun signOut() = viewModelScope.launch {
        _busy.value = true
        try { authRepository.signOut() } catch (e: Exception) { _message.value = friendlyError(e) }
        finally { _busy.value = false }
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty().lowercase()
        return when {
            raw.contains("rate limit") || raw.contains("too many") -> "Too many attempts. Please wait a moment and try again."
            raw.contains("network") || raw.contains("timeout") || raw.contains("connection") -> "Check your internet connection and try again."
            raw.contains("redirect") || raw.contains("not allowed") -> "The confirmation link is not configured. Please try again later."
            else -> "Something went wrong. Please try again."
        }
    }
}
