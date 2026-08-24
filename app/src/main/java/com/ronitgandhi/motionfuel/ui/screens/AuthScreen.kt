package com.ronitgandhi.motionfuel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.auth.AuthFormMode
import com.ronitgandhi.motionfuel.auth.ClerkAuthUiState
import com.ronitgandhi.motionfuel.ui.components.BrandMark

@Composable
fun ClerkAuthScreen(
    state: ClerkAuthUiState,
    onModeChanged: (AuthFormMode) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onVerifyEmail: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark()
                    Spacer(Modifier.padding(6.dp))
                    Column {
                        Text("MotionFuel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text("Secure account access by Clerk", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (state.needsEmailVerification) {
                    Text("Verify your email", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Enter the one-time code Clerk sent to $email.")
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Verification code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onVerifyEmail(code) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (state.busy) CircularProgressIndicator() else Text("Verify and continue")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { onModeChanged(AuthFormMode.SIGN_IN) },
                            enabled = state.formMode != AuthFormMode.SIGN_IN,
                            modifier = Modifier.weight(1f),
                        ) { Text("Sign in") }
                        TextButton(
                            onClick = { onModeChanged(AuthFormMode.SIGN_UP) },
                            enabled = state.formMode != AuthFormMode.SIGN_UP,
                            modifier = Modifier.weight(1f),
                        ) { Text("Create account") }
                    }
                    Text(
                        if (state.formMode == AuthFormMode.SIGN_IN) "Welcome back" else "Create your MotionFuel account",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            if (state.formMode == AuthFormMode.SIGN_IN) onSignIn(email, password)
                            else onSignUp(email, password)
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (state.busy) CircularProgressIndicator()
                        else Text(if (state.formMode == AuthFormMode.SIGN_IN) "Sign in with Clerk" else "Create account with Clerk")
                    }
                }

                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Your password is handled by Clerk and is never stored by MotionFuel.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ClerkConfigurationRequiredScreen() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark()
                Text("Connect Clerk", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Copy secrets.properties.example to secrets.properties, add CLERK_PUBLISHABLE_KEY, then enable Clerk’s Native API and rebuild the app.")
                Text(
                    "Only the publishable key belongs in the Android configuration. Keep the Clerk secret key on the membership server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AuthenticationLoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Securing your session…")
        }
    }
}
