package com.ronitgandhi.motionfuel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.longErrorMessageOrNull
import com.clerk.api.network.serialization.onFailure
import com.clerk.api.network.serialization.onSuccess
import com.clerk.api.signin.SignIn
import com.clerk.api.signup.SignUp
import com.clerk.api.signup.attemptVerification
import com.clerk.api.signup.prepareVerification
import com.ronitgandhi.motionfuel.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthLifecycle { CONFIGURATION_REQUIRED, LOADING, SIGNED_OUT, SIGNED_IN }
enum class AuthFormMode { SIGN_IN, SIGN_UP }

data class ClerkAuthUiState(
    val lifecycle: AuthLifecycle = AuthLifecycle.LOADING,
    val formMode: AuthFormMode = AuthFormMode.SIGN_IN,
    val busy: Boolean = false,
    val needsEmailVerification: Boolean = false,
    val message: String? = null,
    val displayName: String = "MotionFuel member",
    val emailAddress: String? = null,
)

class ClerkAuthViewModel : ViewModel() {
    // Holds the complete authentication state observed by the Compose UI.
    private val mutableState = MutableStateFlow(ClerkAuthUiState())
    val state = mutableState.asStateFlow()

    init {
        // Shows setup guidance when Clerk has not been configured locally.
        if (!AppConfig.isClerkConfigured) {
            mutableState.value = ClerkAuthUiState(lifecycle = AuthLifecycle.CONFIGURATION_REQUIRED)
        } else {
            // Combines Clerk initialisation and user flows into one screen lifecycle.
            combine(Clerk.isInitialized, Clerk.userFlow) { initialized, user -> initialized to user }
                .onEach { (initialized, user) ->
                    mutableState.update { current ->
                        when {
                            !initialized -> current.copy(lifecycle = AuthLifecycle.LOADING)
                            user == null -> current.copy(
                                lifecycle = AuthLifecycle.SIGNED_OUT,
                                busy = false,
                            )
                            else -> {
                                val name = listOfNotNull(user.firstName, user.lastName)
                                    .joinToString(" ")
                                    .ifBlank { user.username ?: "MotionFuel member" }
                                val email = user.emailAddresses
                                    .firstOrNull { it.id == user.primaryEmailAddressId }
                                    ?.emailAddress
                                    ?: user.emailAddresses.firstOrNull()?.emailAddress
                                current.copy(
                                    lifecycle = AuthLifecycle.SIGNED_IN,
                                    busy = false,
                                    message = null,
                                    displayName = name,
                                    emailAddress = email,
                                )
                            }
                        }
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    // Switches between sign-in and account-creation forms without losing auth state.
    fun selectMode(mode: AuthFormMode) {
        mutableState.update {
            it.copy(formMode = mode, needsEmailVerification = false, message = null)
        }
    }

    // Sends the supplied email and password to Clerk's password sign-in flow.
    fun signIn(email: String, password: String) {
        if (!validate(email, password)) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            SignIn.create(
                SignIn.CreateParams.Strategy.Password(
                    identifier = email.trim(),
                    password = password,
                ),
            )
                .onSuccess { signIn ->
                    if (signIn.status != SignIn.Status.COMPLETE) {
                        mutableState.update {
                            it.copy(
                                busy = false,
                                message = "This account requires another sign-in factor that is not enabled in this MVP.",
                            )
                        }
                    }
                }
                .onFailure { failure -> showFailure(failure.longErrorMessageOrNull, failure.throwable) }
        }
    }

    // Creates a Clerk account and requests email verification when required.
    fun signUp(email: String, password: String) {
        if (!validate(email, password)) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            SignUp.create(
                SignUp.CreateParams.Standard(
                    emailAddress = email.trim(),
                    password = password,
                ),
            )
                .onSuccess { signUp ->
                    if (signUp.status == SignUp.Status.COMPLETE) {
                        mutableState.update { it.copy(busy = false) }
                    } else {
                        signUp.prepareVerification(SignUp.PrepareVerificationParams.Strategy.EmailCode())
                            .onSuccess {
                                mutableState.update {
                                    it.copy(
                                        busy = false,
                                        needsEmailVerification = true,
                                        message = "Check your email for the verification code.",
                                    )
                                }
                            }
                            .onFailure { failure ->
                                showFailure(failure.longErrorMessageOrNull, failure.throwable)
                            }
                    }
                }
                .onFailure { failure -> showFailure(failure.longErrorMessageOrNull, failure.throwable) }
        }
    }

    // Completes Clerk's email-code verification for the in-progress sign-up.
    fun verifyEmail(code: String) {
        if (code.isBlank()) {
            mutableState.update { it.copy(message = "Enter the verification code from your email.") }
            return
        }
        val signUp = Clerk.signUp ?: run {
            mutableState.update { it.copy(message = "The sign-up session expired. Please start again.") }
            return
        }
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            signUp.attemptVerification(SignUp.AttemptVerificationParams.EmailCode(code.trim()))
                .onSuccess { result ->
                    if (result.status != SignUp.Status.COMPLETE) {
                        mutableState.update {
                            it.copy(busy = false, message = "That code was not accepted. Please try again.")
                        }
                    }
                }
                .onFailure { failure -> showFailure(failure.longErrorMessageOrNull, failure.throwable) }
        }
    }

    // Ends the active Clerk session and returns the app to its sign-in screen.
    fun signOut() {
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            Clerk.signOut()
                .onSuccess {
                    mutableState.update {
                        ClerkAuthUiState(
                            lifecycle = AuthLifecycle.SIGNED_OUT,
                            message = "Signed out securely.",
                        )
                    }
                }
                .onFailure { failure -> showFailure(failure.longErrorMessageOrNull, failure.throwable) }
        }
    }

    // Rejects clearly invalid credentials before starting a network request.
    private fun validate(email: String, password: String): Boolean {
        val message = when {
            !email.contains('@') -> "Enter a valid email address."
            password.length < 8 -> "Use a password with at least 8 characters."
            else -> null
        }
        if (message != null) mutableState.update { it.copy(message = message) }
        return message == null
    }

    // Converts Clerk or network failures into a user-readable UI message.
    private fun showFailure(message: String?, throwable: Throwable?) {
        mutableState.update {
            it.copy(
                busy = false,
                message = message ?: throwable?.localizedMessage ?: "Authentication could not be completed.",
            )
        }
    }
}
