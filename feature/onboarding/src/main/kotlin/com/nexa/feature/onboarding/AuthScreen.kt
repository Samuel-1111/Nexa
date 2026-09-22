package com.nexa.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Login = email + password. Create account = email + password + confirm
 * password. No confirmation link, no OTP, nothing to verify -- the account
 * is auto-confirmed server-side (migration 019) and signup returns an
 * active session immediately, straight into onboarding.
 */
@Composable
fun AuthScreen(
    initialCreateAccount: Boolean = false,
    viewModel: AuthViewModel = hiltViewModel(),
    onSignedIn: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var createAccount by rememberSaveable { mutableStateOf(initialCreateAccount) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding()
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
            Spacer(Modifier.height(30.dp))
            Text(
                "NEXA", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (createAccount) "Create your account" else "Welcome back",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (createAccount) "Enter your Gmail and choose a password to get started."
                else "Sign in with your Gmail and password.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Email address") }, singleLine = true, shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            )
            if (createAccount) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = confirmPassword, onValueChange = { confirmPassword = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm password") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (createAccount) viewModel.createAccount(email, password, confirmPassword) {}
                    else viewModel.signIn(email, password) { onSignedIn() }
                },
                enabled = !busy && email.trim().contains("@") && password.isNotBlank() && (!createAccount || confirmPassword.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(if (createAccount) "Create account" else "Sign in", fontWeight = FontWeight.SemiBold)
            }
            // Errors only -- always a short human sentence, shown in red,
            // never a raw exception/request dump.
            message?.let {
                Spacer(Modifier.height(12.dp))
                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFEBEE),
                ) {
                    Text(
                        it, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color(0xFFC62828),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { createAccount = !createAccount; viewModel.clearMessage() }) {
                Text(if (createAccount) "Already have an account? Sign in" else "Create a NEXA account")
            }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}
