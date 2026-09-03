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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.auth.AuthFormMode
import com.ronitgandhi.motionfuel.auth.FirebaseAuthUiState
import com.ronitgandhi.motionfuel.auth.SignUpRequest
import com.ronitgandhi.motionfuel.domain.algorithm.CalculateMaintenanceCaloriesUseCase
import com.ronitgandhi.motionfuel.domain.model.ActivityLevel
import com.ronitgandhi.motionfuel.domain.model.BiologicalSex
import com.ronitgandhi.motionfuel.ui.components.BrandMark

@Composable
fun FirebaseAuthScreen(
    state: FirebaseAuthUiState,
    onModeChanged: (AuthFormMode) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (SignUpRequest) -> Unit,
    onResetPassword: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(BiologicalSex.MALE) }
    var activity by remember { mutableStateOf(ActivityLevel.MODERATE) }
    var signUpStep by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark()
                    Spacer(Modifier.padding(6.dp))
                    Column {
                        Text("MotionFuel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text("Move smarter. Fuel with context.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when (state.formMode) {
                    AuthFormMode.SIGN_IN -> SignInForm(email, password, state.busy, { email = it }, { password = it }, { onSignIn(email, password) }, { onModeChanged(AuthFormMode.FORGOT_PASSWORD) }, { onModeChanged(AuthFormMode.SIGN_UP) })
                    AuthFormMode.FORGOT_PASSWORD -> ResetForm(email, state.busy, { email = it }, { onResetPassword(email) }, { onModeChanged(AuthFormMode.SIGN_IN) })
                    AuthFormMode.SIGN_UP -> {
                        Text("Create your account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Step ${signUpStep + 1} of 3", color = MaterialTheme.colorScheme.primary)
                        when (signUpStep) {
                            0 -> AccountFields(name, email, password, confirmPassword, { name = it }, { email = it }, { password = it }, { confirmPassword = it })
                            1 -> ProfileFields(age, height, weight, sex, { age = it }, { height = it }, { weight = it }, { sex = it })
                            else -> ReviewFields(age, height, weight, sex, activity) { activity = it }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = { if (signUpStep == 0) onModeChanged(AuthFormMode.SIGN_IN) else signUpStep-- }, modifier = Modifier.weight(1f)) { Text("Back") }
                            Button(
                                onClick = {
                                    if (signUpStep < 2) signUpStep++ else onSignUp(SignUpRequest(name, email, password, confirmPassword, age.toIntOrNull() ?: 0, sex, height.toDoubleOrNull() ?: 0.0, weight.toDoubleOrNull() ?: 0.0, activity))
                                },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f).height(50.dp),
                            ) { if (state.busy) CircularProgressIndicator() else Text(if (signUpStep < 2) "Continue" else "Create account") }
                        }
                    }
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                Text("Firebase securely manages your password and signed-in session.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SignInForm(email: String, password: String, busy: Boolean, onEmail: (String) -> Unit, onPassword: (String) -> Unit, onSubmit: () -> Unit, onForgot: () -> Unit, onCreate: () -> Unit) {
    Text("Welcome back", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    AuthTextField(email, onEmail, "Email", KeyboardType.Email)
    AuthTextField(password, onPassword, "Password", hidden = true)
    TextButton(onClick = onForgot, modifier = Modifier.fillMaxWidth()) { Text("Forgot password?") }
    Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { if (busy) CircularProgressIndicator() else Text("Log in") }
    TextButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Create a MotionFuel account") }
}

@Composable
private fun ResetForm(email: String, busy: Boolean, onEmail: (String) -> Unit, onSubmit: () -> Unit, onBack: () -> Unit) {
    Text("Reset password", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("Firebase will email you a secure password-reset link.")
    AuthTextField(email, onEmail, "Email", KeyboardType.Email)
    Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { if (busy) CircularProgressIndicator() else Text("Send reset link") }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to login") }
}

@Composable
private fun AccountFields(name: String, email: String, password: String, confirm: String, onName: (String) -> Unit, onEmail: (String) -> Unit, onPassword: (String) -> Unit, onConfirm: (String) -> Unit) {
    AuthTextField(name, onName, "Name")
    AuthTextField(email, onEmail, "Email", KeyboardType.Email)
    AuthTextField(password, onPassword, "Password", hidden = true)
    AuthTextField(confirm, onConfirm, "Confirm password", hidden = true)
}

@Composable
private fun ProfileFields(age: String, height: String, weight: String, sex: BiologicalSex, onAge: (String) -> Unit, onHeight: (String) -> Unit, onWeight: (String) -> Unit, onSex: (BiologicalSex) -> Unit) {
    Text("Personal details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("These values calculate your estimated maintenance calories.")
    AuthTextField(age, onAge, "Age", KeyboardType.Number)
    AuthTextField(height, onHeight, "Height (cm)", KeyboardType.Decimal)
    AuthTextField(weight, onWeight, "Weight (kg)", KeyboardType.Decimal)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BiologicalSex.entries.forEach { option -> FilterChip(selected = sex == option, onClick = { onSex(option) }, label = { Text(option.name.lowercase().replaceFirstChar(Char::uppercase)) }) }
    }
}

@Composable
private fun ReviewFields(age: String, height: String, weight: String, sex: BiologicalSex, activity: ActivityLevel, onActivity: (ActivityLevel) -> Unit) {
    Text("Activity level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    ActivityLevel.entries.forEach { level -> FilterChip(selected = activity == level, onClick = { onActivity(level) }, label = { Text("${level.label} • ${level.factor}") }) }
    val estimate = runCatching { CalculateMaintenanceCaloriesUseCase()(age.toInt(), sex, height.toDouble(), weight.toDouble(), activity).tdeeKcal }.getOrNull()
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Estimated maintenance", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(estimate?.let { "$it kcal/day" } ?: "Complete your details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("This wellness estimate is not medical advice.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AuthTextField(value: String, onValue: (String) -> Unit, label: String, keyboardType: KeyboardType = KeyboardType.Text, hidden: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun FirebaseConfigurationRequiredScreen() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark()
                Text("Connect Firebase", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Register com.ronitgandhi.motionfuel in Firebase, enable Email/Password Authentication, then place google-services.json inside the app folder and rebuild.")
            }
        }
    }
}

@Composable
fun EmailVerificationRequiredScreen(
    email: String?,
    busy: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onResend: () -> Unit,
    onSignOut: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark()
                Text("Verify your email", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Open the verification link sent to ${email ?: "your email address"}, then return here.")
                Button(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp)) else Text("I've verified my email")
                }
                TextButton(onClick = onResend, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Resend email") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Use another account") }
            }
        }
    }
}

@Composable
fun ProfileIncompleteScreen(
    busy: Boolean,
    message: String?,
    onComplete: (Int, BiologicalSex, Double, Double, ActivityLevel) -> Unit,
    onSignOut: () -> Unit,
) {
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(BiologicalSex.MALE) }
    var activity by remember { mutableStateOf(ActivityLevel.MODERATE) }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark()
                Text("Profile setup incomplete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Your Firebase account exists. Complete the profile so Firestore can finish setup without creating another account.")
                ProfileFields(age, height, weight, sex, { age = it }, { height = it }, { weight = it }, { sex = it })
                ReviewFields(age, height, weight, sex, activity) { activity = it }
                Button(
                    onClick = { onComplete(age.toIntOrNull() ?: 0, sex, height.toDoubleOrNull() ?: 0.0, weight.toDoubleOrNull() ?: 0.0, activity) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { if (busy) CircularProgressIndicator() else Text("Complete profile") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            }
        }
    }
}

@Composable
fun AuthenticationLoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun AuthenticationErrorScreen(message: String?, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark()
                Text("Could not load your profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(message ?: "Check your internet connection and Firebase configuration, then try again.")
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            }
        }
    }
}
