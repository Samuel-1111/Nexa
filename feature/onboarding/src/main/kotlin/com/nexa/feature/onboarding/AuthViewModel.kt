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

    fun signUp(email: String, password: String, confirmPassword: String, onOtpSent: (String) -> Unit) = viewModelScope.launch {
        _busy.value = true
        _message.value = null
        try {
            val cleanEmail = email.trim()
            require(cleanEmail.contains("@") && cleanEmail.contains(".")) { "Enter a valid email address." }
            require(password == confirmPassword) { "Passwords do not match." }
            require(password.length >= 6) { "Password must be at least 6 characters." }
            authRepository.signUpWithEmail(cleanEmail, password)
            onOtpSent(cleanEmail)
        } catch (e: Exception) {
            _message.value = friendlyError(e)
        } finally {
            _busy.value = false
        }
    }

    fun verifyOtp(email: String, token: String, onVerified: () -> Unit) = viewModelScope.launch {
        _busy.value = true
        _message.value = null
        try {
            require(token.trim().matches(Regex("\\d{6}"))) {
                "Enter the 6-digit code from your email."
            }
            authRepository.verifySignupOtp(email.trim(), token.trim())
            onVerified()
        } catch (e: Exception) {
            _message.value = friendlyError(e)
        } finally {
            _busy.value = false
        }
    }

    fun resendOtp(email: String) = viewModelScope.launch {
        _busy.value = true
        _message.value = null
        try {
            authRepository.resendSignupOtp(email.trim())
            _message.value = "A new verification code has been sent."
        } catch (e: Exception) {
            _message.value = friendlyError(e)
        } finally {
            _busy.value = false
        }
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        _busy.value = true
        _message.value = null
        try {
            authRepository.signInWithEmail(email.trim(), password)
            _message.value = "Signed in to NEXA."
        } catch (e: Exception) {
            _message.value = friendlyError(e)
        } finally {
            _busy.value = false
        }
    }

    fun sendPasswordReset(email: String) = viewModelScope.launch {
        _busy.value = true
        _message.value = null
        try {
            authRepository.sendPasswordResetEmail(email.trim())
            _message.value = "Password reset instructions have been sent."
        } catch (e: Exception) {
            _message.value = friendlyError(e)
        } finally {
            _busy.value = false
        }
    }

    fun updatePassword(password: String, confirmPassword: String) = viewModelScope.launch {
        _busy.value = true
        _message.value = null
        try {
            require(password == confirmPassword) { "Passwords do not match." }
            require(password.length >= 6) { "Password must be at least 6 characters." }
            authRepository.updatePassword(password)
            _message.value = "Password updated successfully."
        } catch (e: Exception) {
            _message.value = friendlyError(e)
        } finally {
            _busy.value = false
        }
    }

    fun signOut() = viewModelScope.launch {
        _busy.value = true
        try {
            authRepository.signOut()
        } catch (e: Exception) {
            _message.value = friendlyError(e)
        } finally {
            _busy.value = false
        }
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty().lowercase()
        return when {
            raw.contains("invalid login credentials") -> "Email or password is incorrect."
            raw.contains("user already registered") -> "An account with this email already exists."
            raw.contains("email not confirmed") -> "Please enter the verification code sent to your email."
            raw.contains("token has expired") || raw.contains("otp expired") -> "That code has expired. Request a new one."
            raw.contains("invalid token") || raw.contains("invalid otp") || raw.contains("invalid verification") || (raw.contains("invalid") && raw.contains("otp")) -> "Incorrect verification code. Please try again."
            raw.contains("rate limit") || raw.contains("too many") -> "Too many attempts. Please wait a moment and try again."
            raw.contains("network") || raw.contains("timeout") || raw.contains("connection") -> "Check your internet connection and try again."
            raw.contains("already registered") -> "An account with this email already exists."
            else -> "Something went wrong. Please try again."
        }
    }
}
