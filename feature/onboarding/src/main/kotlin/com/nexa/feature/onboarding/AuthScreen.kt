package com.nexa.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AuthScreen(
    initialCreateAccount: Boolean = false,
    viewModel: AuthViewModel = hiltViewModel(),
    onOtpRequested: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var createAccount by rememberSaveable { mutableStateOf(initialCreateAccount) }
    var resetPassword by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 520.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                "NEXA",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    resetPassword -> "Reset your password"
                    createAccount -> "Create your account"
                    else -> "Welcome back"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    resetPassword -> "We’ll help you get back into NEXA."
                    createAccount -> "Create your NEXA account. We’ll send a 6-digit code to your email."
                    else -> "Sign in and get back to your day."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(16.dp),
            )

            if (!resetPassword) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                )

                if (createAccount) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    when {
                        resetPassword -> viewModel.sendPasswordReset(email)
                        createAccount -> viewModel.signUp(email, password, confirmPassword, onOtpRequested)
                        else -> viewModel.signIn(email, password)
                    }
                },
                enabled = !busy && email.isNotBlank() && (resetPassword || password.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(
                    when {
                        resetPassword -> "Send reset instructions"
                        createAccount -> "Create account"
                        else -> "Log in"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Errors are deliberately reduced to one short, human-readable line.
            // Raw Supabase/HTTP exception dumps are never rendered here.
            message?.let {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFEBEE),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color(0xFFC62828),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            if (!resetPassword && !createAccount) {
                TextButton(onClick = { resetPassword = true; viewModel.clearMessage() }) {
                    Text("Forgot password?")
                }
            }

            TextButton(
                onClick = {
                    resetPassword = false
                    createAccount = !createAccount
                    viewModel.clearMessage()
                },
            ) {
                Text(if (createAccount) "Already have an account? Log in" else "Create a NEXA account")
            }

            if (resetPassword) {
                OutlinedButton(
                    onClick = { resetPassword = false; viewModel.clearMessage() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Back to login")
                }
            } else {
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
    }
}
