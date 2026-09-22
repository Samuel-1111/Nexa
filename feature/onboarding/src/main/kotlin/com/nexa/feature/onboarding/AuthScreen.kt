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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

/**
 * NEXA authentication:
 * Login = Gmail + password.
 * Create account = Gmail + password + confirm password.
 *
 * There is no signup confirmation screen, confirmation-link action, OTP
 * screen, or resend-confirmation action. Signup returns an authenticated
 * session and continues directly to the personalization questions.
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
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showConfirmPassword by rememberSaveable { mutableStateOf(false) }

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
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
            Spacer(Modifier.height(30.dp))
            Text(
                "NEXA",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (createAccount) "Create your account" else "Welcome back",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (createAccount) {
                    "Create your NEXA account with your Gmail and password."
                } else {
                    "Sign in with your Gmail and password."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Gmail") },
                placeholder = { Text("you@gmail.com") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                        )
                    }
                },
            )

            if (createAccount) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm password") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                            Icon(
                                imageVector = if (showConfirmPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showConfirmPassword) "Hide password" else "Show password",
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (createAccount) {
                        viewModel.createAccount(email, password, confirmPassword) {}
                    } else {
                        viewModel.signIn(email, password) { onSignedIn() }
                    }
                },
                enabled = !busy &&
                    email.trim().isNotBlank() &&
                    password.isNotBlank() &&
                    (!createAccount || confirmPassword.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(if (createAccount) "Create account" else "Sign in", fontWeight = FontWeight.SemiBold)
            }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFEBEE),
                ) {
                    Text(
                        it,
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color(0xFFC62828),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    createAccount = !createAccount
                    password = ""
                    confirmPassword = ""
                    showPassword = false
                    showConfirmPassword = false
                    viewModel.clearMessage()
                },
            ) {
                Text(if (createAccount) "Already have an account? Sign in" else "Create a NEXA account")
            }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}
