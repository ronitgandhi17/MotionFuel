package com.ronitgandhi.motionfuel.auth

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.ronitgandhi.motionfuel.domain.algorithm.CalculateMaintenanceCaloriesUseCase
import com.ronitgandhi.motionfuel.domain.algorithm.ProfileUpdateValidator
import com.ronitgandhi.motionfuel.domain.model.ActivityLevel
import com.ronitgandhi.motionfuel.domain.model.BiologicalSex
import com.ronitgandhi.motionfuel.domain.model.ProfileUpdate
import com.ronitgandhi.motionfuel.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

enum class AuthLifecycle { CONFIGURATION_REQUIRED, LOADING, AUTHENTICATION_ERROR, SIGNED_OUT, EMAIL_VERIFICATION_REQUIRED, PROFILE_INCOMPLETE, SIGNED_IN }
enum class AuthFormMode { SIGN_IN, SIGN_UP, FORGOT_PASSWORD }

data class SignUpRequest(
    val name: String,
    val email: String,
    val password: String,
    val confirmPassword: String,
    val age: Int,
    val sex: BiologicalSex,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: ActivityLevel,
    val profilePhotoUri: String? = null,
)

data class FirebaseAuthUiState(
    val lifecycle: AuthLifecycle = AuthLifecycle.LOADING,
    val formMode: AuthFormMode = AuthFormMode.SIGN_IN,
    val busy: Boolean = false,
    val message: String? = null,
    val profile: UserProfile? = null,
    val verificationEmail: String? = null,
)

class FirebaseAuthViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(FirebaseAuthUiState())
    val state = mutableState.asStateFlow()
    private val calculator = CalculateMaintenanceCaloriesUseCase()
    private val firebaseReady = FirebaseApp.getApps(application).isNotEmpty()
    private val auth: FirebaseAuth? = if (firebaseReady) FirebaseAuth.getInstance() else null
    private val firestore: FirebaseFirestore? = if (firebaseReady) FirebaseFirestore.getInstance() else null
    private val storage: FirebaseStorage? = if (firebaseReady) FirebaseStorage.getInstance() else null
    private val authListener = AuthStateListener { firebaseAuth -> handleUser(firebaseAuth.currentUser) }

    init {
        // Keeps Compose navigation synchronized with Firebase's persisted authentication session.
        if (!firebaseReady) mutableState.value = FirebaseAuthUiState(AuthLifecycle.CONFIGURATION_REQUIRED)
        else auth?.addAuthStateListener(authListener)
    }

    override fun onCleared() {
        auth?.removeAuthStateListener(authListener)
        super.onCleared()
    }

    fun selectMode(mode: AuthFormMode) = mutableState.update { it.copy(formMode = mode, message = null) }

    // Authenticates an existing Firebase email/password account.
    fun signIn(email: String, password: String) {
        if (!validCredentials(email, password)) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { requireNotNull(auth).signInWithEmailAndPassword(email.trim(), password).await() }
                .onFailure(::showFailure)
        }
    }

    // Creates the Firebase account and stores its completed TDEE profile under users/{uid}.
    fun signUp(request: SignUpRequest) {
        val error = validateSignUp(request)
        if (error != null) {
            mutableState.update { it.copy(message = error) }
            return
        }
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val result = requireNotNull(auth).createUserWithEmailAndPassword(request.email.trim(), request.password).await()
                val user = requireNotNull(result.user)
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(request.name.trim()).build()).await()
                val calories = calculator(request.age, request.sex, request.heightCm, request.weightKg, request.activityLevel)
                var photoUploadFailed = false
                val photoUrl = request.profilePhotoUri?.let { uri ->
                    runCatching { uploadProfilePhoto(user.uid, uri) }
                        .onFailure { photoUploadFailed = true }
                        .getOrNull()
                }
                val profile = UserProfile(
                    userId = user.uid,
                    name = request.name.trim(),
                    email = request.email.trim(),
                    age = request.age,
                    sex = request.sex,
                    heightCm = request.heightCm,
                    weightKg = request.weightKg,
                    activityLevel = request.activityLevel,
                    maintenanceCaloriesKcal = calories.tdeeKcal,
                    dailyCalorieGoalKcal = calories.tdeeKcal,
                    photoUrl = photoUrl,
                )
                requireNotNull(firestore).collection("users").document(user.uid).set(profile.toFirestore()).await()
                val verificationSent = runCatching { user.sendEmailVerification().await() }.isSuccess
                mutableState.value = FirebaseAuthUiState(
                    lifecycle = AuthLifecycle.EMAIL_VERIFICATION_REQUIRED,
                    verificationEmail = user.email,
                    message = when {
                        photoUploadFailed -> "Account created. Verify your email; the profile picture could not be uploaded, so you can add it later from Edit profile."
                        verificationSent -> "Verification email sent."
                        else -> "Use Resend email to request a new verification link."
                    },
                )
            }.onFailure { failure ->
                val accountExists = auth?.currentUser != null
                mutableState.update {
                    it.copy(
                        lifecycle = if (accountExists) AuthLifecycle.PROFILE_INCOMPLETE else AuthLifecycle.SIGNED_OUT,
                        busy = false,
                        message = failure.localizedMessage ?: "Account setup could not be completed.",
                    )
                }
            }
        }
    }

    // Sends Firebase's password-reset email without revealing whether an account exists.
    fun resetPassword(email: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            mutableState.update { it.copy(message = "Enter a valid email address.") }
            return
        }
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { requireNotNull(auth).sendPasswordResetEmail(email.trim()).await() }
                .onSuccess {
                    mutableState.update {
                        it.copy(busy = false, formMode = AuthFormMode.SIGN_IN, message = "Password reset email sent.")
                    }
                }
                .onFailure(::showFailure)
        }
    }

    // Repairs a signed-in account whose Firestore profile creation was interrupted.
    fun completeExistingProfile(age: Int, sex: BiologicalSex, heightCm: Double, weightKg: Double, activityLevel: ActivityLevel) {
        val user = auth?.currentUser ?: return
        val request = SignUpRequest(user.displayName ?: "MotionFuel member", user.email.orEmpty(), "12345678", "12345678", age, sex, heightCm, weightKg, activityLevel)
        val error = validateSignUp(request)
        if (error != null) {
            mutableState.update { it.copy(message = error) }
            return
        }
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val calories = calculator(age, sex, heightCm, weightKg, activityLevel)
                val profile = UserProfile(user.uid, request.name, request.email, age, sex, heightCm, weightKg, activityLevel, calories.tdeeKcal, calories.tdeeKcal)
                requireNotNull(firestore).collection("users").document(user.uid).set(profile.toFirestore()).await()
                mutableState.value = FirebaseAuthUiState(AuthLifecycle.SIGNED_IN, profile = profile)
            }.onFailure(::showFailure)
        }
    }

    // Recalculates TDEE after a new current weight and preserves any custom calorie goal.
    fun updateCurrentWeight(weightKg: Double) {
        val current = mutableState.value.profile ?: return
        val calories = calculator(current.age, current.sex, current.heightCm, weightKg, current.activityLevel)
        val updated = current.copy(weightKg = weightKg, maintenanceCaloriesKcal = calories.tdeeKcal)
        mutableState.update { it.copy(profile = updated) }
        viewModelScope.launch {
            runCatching {
                val userDocument = requireNotNull(firestore).collection("users").document(current.userId)
                userDocument.set(
                    mapOf(
                        "weightKg" to weightKg,
                        "maintenanceCaloriesKcal" to calories.tdeeKcal,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                ).await()
                userDocument.collection("weightEntries").add(
                    mapOf("weightKg" to weightKg, "recordedAt" to FieldValue.serverTimestamp()),
                ).await()
            }.onFailure(::showFailure)
        }
    }

    // Updates editable profile fields while preserving the Firebase-authenticated email address.
    fun updateProfile(update: ProfileUpdate) {
        val current = mutableState.value.profile ?: return
        val user = auth?.currentUser ?: return
        ProfileUpdateValidator.validate(update)?.let { error ->
            mutableState.update { it.copy(message = error) }
            return
        }
        val normalized = update.copy(name = update.name.trim())
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val calories = calculator(normalized.age, normalized.sex, normalized.heightCm, normalized.weightKg, normalized.activityLevel)
                val photoChanged = normalized.profilePhotoUri != current.photoUrl
                val photoUrl = when {
                    !photoChanged -> current.photoUrl
                    normalized.profilePhotoUri == null -> null
                    else -> uploadProfilePhoto(current.userId, normalized.profilePhotoUri)
                }
                val updated = current.copy(
                    name = normalized.name,
                    age = normalized.age,
                    sex = normalized.sex,
                    heightCm = normalized.heightCm,
                    weightKg = normalized.weightKg,
                    activityLevel = normalized.activityLevel,
                    maintenanceCaloriesKcal = calories.tdeeKcal,
                    dailyCalorieGoalKcal = normalized.dailyCalorieGoalKcal,
                    photoUrl = photoUrl,
                )
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(normalized.name).build()).await()
                val userDocument = requireNotNull(firestore).collection("users").document(current.userId)
                val batch = requireNotNull(firestore).batch()
                batch.set(
                    userDocument,
                    mapOf(
                        "name" to updated.name,
                        "age" to updated.age,
                        "sex" to updated.sex.name,
                        "heightCm" to updated.heightCm,
                        "weightKg" to updated.weightKg,
                        "activityLevel" to updated.activityLevel.name,
                        "activityFactor" to updated.activityLevel.factor,
                        "maintenanceCaloriesKcal" to updated.maintenanceCaloriesKcal,
                        "dailyCalorieGoalKcal" to updated.dailyCalorieGoalKcal,
                        "photoUrl" to updated.photoUrl,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
                if (updated.weightKg != current.weightKg) {
                    batch.set(
                        userDocument.collection("weightEntries").document(),
                        mapOf("weightKg" to updated.weightKg, "recordedAt" to FieldValue.serverTimestamp()),
                    )
                }
                batch.commit().await()
                if (photoChanged && normalized.profilePhotoUri == null) {
                    runCatching { requireNotNull(storage).reference.child("profile-images/${current.userId}/avatar").delete().await() }
                }
                updated
            }.onSuccess { updated ->
                mutableState.update { it.copy(profile = updated, busy = false, message = "Profile updated.") }
            }.onFailure(::showFailure)
        }
    }

    fun signOut() {
        auth?.signOut()
    }

    // Compresses a content URI to a bounded JPEG before uploading it to the user's fixed Storage path.
    private suspend fun uploadProfilePhoto(userId: String, uriValue: String): String = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val sourceUri = Uri.parse(uriValue)
        require(sourceUri.scheme == "content") { "Choose a picture from Camera or Gallery." }
        val contentType = context.contentResolver.getType(sourceUri)
        require(contentType?.startsWith("image/") == true) { "The selected file must be an image." }
        val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largestSide = max(info.size.width, info.size.height)
            if (largestSide > 1_024) {
                val scale = 1_024f / largestSide
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        val directory = File(context.cacheDir, "profile_photos").apply { mkdirs() }
        val compressed = File.createTempFile("profile_upload_", ".jpg", directory)
        try {
            FileOutputStream(compressed).use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "The profile picture could not be prepared." }
            }
            val reference = requireNotNull(storage).reference.child("profile-images/$userId/avatar")
            val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
            reference.putFile(Uri.fromFile(compressed), metadata).await()
            reference.downloadUrl.await().toString()
        } finally {
            bitmap.recycle()
            compressed.delete()
        }
    }

    fun resendVerificationEmail() {
        val user = auth?.currentUser ?: return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { user.sendEmailVerification().await() }
                .onSuccess { mutableState.update { it.copy(busy = false, message = "Verification email sent.") } }
                .onFailure(::showFailure)
        }
    }

    fun refreshEmailVerification() {
        val user = auth?.currentUser ?: return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching {
                user.reload().await()
                auth?.currentUser?.also { refreshed ->
                    if (refreshed.isEmailVerified) refreshed.getIdToken(true).await()
                }
            }
                .onSuccess(::handleUser)
                .onFailure(::showFailure)
        }
    }

    // Retries the persisted Firebase session and Firestore profile lookup after a startup failure.
    fun retrySessionLoad() = handleUser(auth?.currentUser)

    // Loads the user-owned Firestore profile whenever Firebase restores or changes a session.
    private fun handleUser(user: FirebaseUser?) {
        if (user == null) {
            mutableState.value = FirebaseAuthUiState(AuthLifecycle.SIGNED_OUT)
            return
        }
        if (!user.isEmailVerified) {
            mutableState.value = FirebaseAuthUiState(
                lifecycle = AuthLifecycle.EMAIL_VERIFICATION_REQUIRED,
                verificationEmail = user.email,
                message = "Verify your email before opening MotionFuel.",
            )
            return
        }
        val uid = user.uid
        mutableState.update { it.copy(lifecycle = AuthLifecycle.LOADING, busy = true) }
        viewModelScope.launch {
            runCatching {
                // Prevents a stalled network request from leaving the app on an endless loading screen.
                withTimeout(15_000L) { requireNotNull(firestore).collection("users").document(uid).get().await() }
            }
                .onSuccess { snapshot ->
                    val profile = snapshot.toProfile(uid)
                    mutableState.value = if (profile == null) {
                        FirebaseAuthUiState(AuthLifecycle.PROFILE_INCOMPLETE, message = "Complete your profile to continue.")
                    } else FirebaseAuthUiState(AuthLifecycle.SIGNED_IN, profile = profile)
                }
                .onFailure { failure ->
                    mutableState.value = FirebaseAuthUiState(
                        lifecycle = AuthLifecycle.AUTHENTICATION_ERROR,
                        message = failure.localizedMessage ?: "Firebase did not respond. Check your connection and Firestore setup.",
                    )
                }
        }
    }

    private fun validateSignUp(request: SignUpRequest): String? = when {
        request.name.trim().length < 2 -> "Enter your name."
        !validCredentials(request.email, request.password, updateState = false) -> "Enter a valid email and a password with at least 8 characters."
        request.password != request.confirmPassword -> "Passwords do not match."
        request.age !in 13..120 -> "Enter an age between 13 and 120."
        request.heightCm !in 100.0..250.0 -> "Enter a height between 100 and 250 cm."
        request.weightKg !in 30.0..350.0 -> "Enter a weight between 30 and 350 kg."
        else -> null
    }

    private fun validCredentials(email: String, password: String, updateState: Boolean = true): Boolean {
        val valid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() && password.length >= 8
        if (!valid && updateState) mutableState.update { it.copy(message = "Enter a valid email and a password with at least 8 characters.") }
        return valid
    }

    private fun showFailure(throwable: Throwable) = mutableState.update {
        it.copy(busy = false, message = throwable.localizedMessage ?: "Firebase request failed. Please try again.")
    }
}

private fun UserProfile.toFirestore() = mapOf(
    "name" to name,
    "email" to email,
    "age" to age,
    "sex" to sex.name,
    "heightCm" to heightCm,
    "weightKg" to weightKg,
    "activityLevel" to activityLevel.name,
    "activityFactor" to activityLevel.factor,
    "maintenanceCaloriesKcal" to maintenanceCaloriesKcal,
    "dailyCalorieGoalKcal" to dailyCalorieGoalKcal,
    "photoUrl" to photoUrl,
    "profileComplete" to profileComplete,
    "createdAt" to FieldValue.serverTimestamp(),
)

private fun com.google.firebase.firestore.DocumentSnapshot.toProfile(uid: String): UserProfile? {
    if (!exists() || getBoolean("profileComplete") != true) return null
    return runCatching {
        UserProfile(
            userId = uid,
            name = requireNotNull(getString("name")),
            email = getString("email").orEmpty(),
            age = requireNotNull(getLong("age")).toInt(),
            sex = BiologicalSex.valueOf(requireNotNull(getString("sex"))),
            heightCm = requireNotNull(getDouble("heightCm")),
            weightKg = requireNotNull(getDouble("weightKg")),
            activityLevel = ActivityLevel.valueOf(requireNotNull(getString("activityLevel"))),
            maintenanceCaloriesKcal = requireNotNull(getLong("maintenanceCaloriesKcal")).toInt(),
            dailyCalorieGoalKcal = requireNotNull(getLong("dailyCalorieGoalKcal")).toInt(),
            photoUrl = getString("photoUrl"),
        )
    }.getOrNull()
}
