package com.nexa.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OtpScreen(
    email: String,
    viewModel: AuthViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onVerified: () -> Unit = {},
) {
    var code by rememberSaveable { mutableStateOf("") }
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 520.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(80.dp))
            Text(
                "NEXA",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter your code",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter the 6-digit code we sent to $email.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Verification code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.verifyOtp(email, code, onVerified) },
                enabled = !busy && code.length == 6,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("Verify and continue", fontWeight = FontWeight.SemiBold)
            }

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
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.resendOtp(email) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Resend code")
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "If you don't see it, check spam or promotions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}
