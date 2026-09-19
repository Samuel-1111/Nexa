package com.nexa.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OtpScreen(email: String, viewModel: AuthViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    var code by rememberSaveable { mutableStateOf("") }
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
            Text("Verify your email", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Enter the 6-digit code we sent to $email.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                code,
                { code = it.filter(Char::isDigit).take(6) },
                Modifier.fillMaxWidth(),
                label = { Text("Verification code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.verifyOtp(email, code) {} },
                enabled = !busy && code.length == 6,
                Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("Verify and continue", fontWeight = FontWeight.SemiBold)
            }
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.resendOtp(email) }, enabled = !busy, Modifier.fillMaxWidth()) { Text("Resend code") }
            Spacer(Modifier.height(6.dp))
            Text("Check your spam or promotions folder if needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}
