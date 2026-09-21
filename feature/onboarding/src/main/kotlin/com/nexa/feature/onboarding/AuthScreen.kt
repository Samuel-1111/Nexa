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
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val lastSignupEmail by viewModel.lastSignupEmail.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding()
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
            Spacer(Modifier.height(30.dp))
            Text("NEXA", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(if (createAccount) "Create your account" else "Welcome back",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (createAccount)
                    "Create your account with your Gmail and password. We’ll send a confirmation link to your email."
                else
                    "Sign in with your Gmail and password.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Email address") }, singleLine = true, shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (createAccount) viewModel.createAccount(email, password) {}
                    else viewModel.signIn(email, password) { onSignedIn() }
                },
                enabled = !busy && email.trim().contains("@") && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(if (createAccount) "Create account" else "Sign in", fontWeight = FontWeight.SemiBold)
            }
            message?.let {
                Spacer(Modifier.height(12.dp))
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = if (it.startsWith("Check your email") || it.startsWith("Account created")) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)) {
                    Text(it, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = if (it.startsWith("Check your email") || it.startsWith("Account created")) Color(0xFF2E7D32) else Color(0xFFC62828),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
            if (createAccount && lastSignupEmail != null) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { viewModel.resendConfirmation() },
                    enabled = !busy,
                ) {
                    Text("Resend confirmation email")
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
