package com.nexa.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var resetPassword by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val busy by viewModel.busy.collectAsStateCompat()
    val message by viewModel.message.collectAsStateCompat()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NEXA", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                resetPassword -> "Reset your password"
                createAccount -> "Create your NEXA account"
                else -> "Welcome back"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                when {
                    resetPassword -> viewModel.sendPasswordReset(email)
                    createAccount -> viewModel.signUp(email, password, confirmPassword)
                    else -> viewModel.signIn(email, password)
                }
            },
            enabled = !busy && email.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text(when {
                resetPassword -> "Send reset email"
                createAccount -> "Create account"
                else -> "Log in"
            })
        }

        Spacer(Modifier.height(10.dp))
        if (!resetPassword && !createAccount) {
            TextButton(onClick = { resetPassword = true }) { Text("Forgot password?") }
        }
        TextButton(onClick = {
            resetPassword = false
            createAccount = !createAccount
        }) {
            Text(if (createAccount) "Already have an account? Log in" else "Create a NEXA account")
        }
        if (resetPassword) {
            OutlinedButton(onClick = { resetPassword = false }) { Text("Back to login") }
        }

        message?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat() =
    androidx.compose.runtime.collectAsState(initial = value)
