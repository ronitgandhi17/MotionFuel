package com.ronitgandhi.motionfuel

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ronitgandhi.motionfuel.auth.AuthLifecycle
import com.ronitgandhi.motionfuel.auth.FirebaseAuthViewModel
import com.ronitgandhi.motionfuel.domain.model.WorkoutStatus
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import com.ronitgandhi.motionfuel.domain.model.UserProfile
import com.ronitgandhi.motionfuel.domain.model.ProfileUpdate
import com.ronitgandhi.motionfuel.ui.screens.ActivityScreen
import com.ronitgandhi.motionfuel.ui.screens.ActivityDetailScreen
import com.ronitgandhi.motionfuel.ui.screens.AuthenticationErrorScreen
import com.ronitgandhi.motionfuel.ui.screens.AuthenticationLoadingScreen
import com.ronitgandhi.motionfuel.ui.screens.FirebaseAuthScreen
import com.ronitgandhi.motionfuel.ui.screens.FirebaseConfigurationRequiredScreen
import com.ronitgandhi.motionfuel.ui.screens.EmailVerificationRequiredScreen
import com.ronitgandhi.motionfuel.ui.screens.FoodScreen
import com.ronitgandhi.motionfuel.ui.screens.ProgressScreen
import com.ronitgandhi.motionfuel.ui.screens.ProfileIncompleteScreen
import com.ronitgandhi.motionfuel.ui.screens.ProfileScreen
import com.ronitgandhi.motionfuel.ui.screens.TodayScreen
import com.ronitgandhi.motionfuel.ui.screens.WorkoutScreen
import com.ronitgandhi.motionfuel.ui.theme.MotionFuelTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            // Gates every dashboard route behind the current Firebase authentication state.
            val authViewModel: FirebaseAuthViewModel = viewModel()
            val auth by authViewModel.state.collectAsStateWithLifecycle()
            when (auth.lifecycle) {
                AuthLifecycle.CONFIGURATION_REQUIRED -> MotionFuelTheme(darkTheme = systemDarkTheme) {
                    FirebaseConfigurationRequiredScreen()
                }
                AuthLifecycle.LOADING -> MotionFuelTheme(darkTheme = systemDarkTheme) {
                    AuthenticationLoadingScreen()
                }
                AuthLifecycle.AUTHENTICATION_ERROR -> MotionFuelTheme(darkTheme = systemDarkTheme) {
                    AuthenticationErrorScreen(
                        message = auth.message,
                        onRetry = authViewModel::retrySessionLoad,
                        onSignOut = authViewModel::signOut,
                    )
                }
                AuthLifecycle.SIGNED_OUT -> MotionFuelTheme(darkTheme = systemDarkTheme) {
                    FirebaseAuthScreen(
                        state = auth,
                        onModeChanged = authViewModel::selectMode,
                        onSignIn = authViewModel::signIn,
                        onSignUp = authViewModel::signUp,
                        onResetPassword = authViewModel::resetPassword,
                    )
                }
                AuthLifecycle.EMAIL_VERIFICATION_REQUIRED -> MotionFuelTheme(darkTheme = systemDarkTheme) {
                    EmailVerificationRequiredScreen(
                        email = auth.verificationEmail,
                        busy = auth.busy,
                        message = auth.message,
                        onRefresh = authViewModel::refreshEmailVerification,
                        onResend = authViewModel::resendVerificationEmail,
                        onSignOut = authViewModel::signOut,
                    )
                }
                AuthLifecycle.PROFILE_INCOMPLETE -> MotionFuelTheme(darkTheme = systemDarkTheme) {
                    ProfileIncompleteScreen(
                        busy = auth.busy,
                        message = auth.message,
                        onComplete = authViewModel::completeExistingProfile,
                        onSignOut = authViewModel::signOut,
                    )
                }
                AuthLifecycle.SIGNED_IN -> {
                    // Creates app state only after Firebase confirms a signed-in user profile.
                    val motionFuelViewModel: MotionFuelViewModel = viewModel()
                    val settings by motionFuelViewModel.settings.collectAsStateWithLifecycle()
                    MotionFuelTheme(darkTheme = settings.darkTheme) {
                        MotionFuelRoot(
                            viewModel = motionFuelViewModel,
                            profile = requireNotNull(auth.profile),
                            profileBusy = auth.busy,
                            profileMessage = auth.message,
                            onWeightChanged = authViewModel::updateCurrentWeight,
                            onUpdateProfile = authViewModel::updateProfile,
                            onSignOut = authViewModel::signOut,
                        )
                    }
                }
            }
        }
    }
}

private enum class MainTab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.Home),
    ACTIVITY("Activity", Icons.Rounded.DirectionsRun),
    FOOD("Food", Icons.Rounded.Restaurant),
    PROGRESS("Progress", Icons.Rounded.ShowChart),
    PROFILE("Profile", Icons.Rounded.Person),
}

@Composable
private fun MotionFuelRoot(
    viewModel: MotionFuelViewModel,
    profile: UserProfile,
    profileBusy: Boolean,
    profileMessage: String?,
    onWeightChanged: (Double) -> Unit,
    onUpdateProfile: (ProfileUpdate) -> Unit,
    onSignOut: () -> Unit,
) {
    LaunchedEffect(profile.weightKg) { viewModel.setProfileWeight(profile.weightKg) }
    // Collects each offline-first data stream as lifecycle-aware Compose state.
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val totals by viewModel.nutritionTotals.collectAsStateWithLifecycle()
    val entries by viewModel.nutritionEntries.collectAsStateWithLifecycle()
    val nutritionHistory by viewModel.nutritionHistory.collectAsStateWithLifecycle()
    val weightEntries by viewModel.weightEntries.collectAsStateWithLifecycle()
    val savedFoods by viewModel.savedFoods.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val weatherStatus by viewModel.weatherStatus.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val foodResults by viewModel.foodResults.collectAsStateWithLifecycle()
    val foodSearchStatus by viewModel.foodSearchStatus.collectAsStateWithLifecycle()
    // Keeps swipe gestures and bottom-navigation selection on the same five-page state.
    val pagerState = rememberPagerState(initialPage = MainTab.TODAY.ordinal, pageCount = { MainTab.entries.size })
    val navigationScope = rememberCoroutineScope()
    var selectedWorkout by remember { mutableStateOf<WorkoutSummary?>(null) }
    var showStartDialog by remember { mutableStateOf(false) }
    var pendingRealType by remember { mutableStateOf<WorkoutType?>(null) }
    var foodIsRootPage by remember { mutableStateOf(true) }
    var profileIsRootPage by remember { mutableStateOf(true) }
    val rootSwipeEnabled = !showStartDialog && when (MainTab.entries[pagerState.currentPage]) {
        MainTab.FOOD -> foodIsRootPage
        MainTab.PROFILE -> profileIsRootPage
        else -> true
    }

    // Starts real tracking only after Android grants at least one location permission.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) pendingRealType?.let(viewModel::startReal)
        pendingRealType = null
    }

    if (telemetry.status != WorkoutStatus.IDLE) {
        WorkoutScreen(
            telemetry = telemetry,
            insights = insights,
            units = settings.units,
            weather = weather,
            weatherStatus = weatherStatus,
            onPauseResume = viewModel::pauseOrResumeWorkout,
            onFinish = viewModel::finishWorkout,
            onDone = {
                viewModel.dismissCompletedWorkout()
                navigationScope.launch { pagerState.scrollToPage(MainTab.ACTIVITY.ordinal) }
            },
        )
    } else if (selectedWorkout != null) {
        ActivityDetailScreen(
            workout = requireNotNull(selectedWorkout),
            units = settings.units,
            darkTheme = settings.darkTheme,
            onBack = { selectedWorkout = null },
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == tab.ordinal,
                            onClick = { navigationScope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = rootSwipeEnabled,
                modifier = Modifier.fillMaxSize().padding(padding),
                key = { MainTab.entries[it].name },
            ) { page ->
                when (MainTab.entries[page]) {
                    MainTab.TODAY -> TodayScreen(
                        profile = profile,
                        workouts = workouts,
                        nutrition = totals,
                        entries = entries,
                        weather = weather,
                        weatherStatus = weatherStatus,
                        insights = insights,
                        settings = settings,
                        onStartWorkout = { showStartDialog = true },
                        onRefreshWeather = viewModel::refreshWeather,
                        onOpenActivity = { navigationScope.launch { pagerState.animateScrollToPage(MainTab.ACTIVITY.ordinal) } },
                        onOpenFood = { navigationScope.launch { pagerState.animateScrollToPage(MainTab.FOOD.ordinal) } },
                    )
                    MainTab.ACTIVITY -> ActivityScreen(
                        workouts = workouts,
                        settings = settings,
                        onStartWorkout = { showStartDialog = true },
                        onActivitySelected = { selectedWorkout = it },
                    )
                    MainTab.FOOD -> FoodScreen(
                        darkTheme = settings.darkTheme,
                        totals = totals,
                        entries = entries,
                        savedFoods = savedFoods,
                        results = foodResults,
                        searchStatus = foodSearchStatus,
                        onSearch = viewModel::searchFoods,
                        onAddFood = viewModel::addFood,
                        onAddManual = viewModel::addManualFood,
                        onAddSavedFood = viewModel::addSavedFood,
                        onDeleteNutritionEntry = viewModel::deleteNutritionEntry,
                        onDeleteSavedFood = viewModel::deleteSavedFood,
                        onRootPageChanged = { foodIsRootPage = it },
                    )
                    MainTab.PROGRESS -> ProgressScreen(
                        nutritionHistory = nutritionHistory,
                        weightEntries = weightEntries,
                        profile = profile,
                        insights = insights,
                        onAddWeight = { weight ->
                            viewModel.addWeight(weight)
                            onWeightChanged(weight)
                        },
                    )
                    MainTab.PROFILE -> ProfileScreen(
                        profile = profile,
                        settings = settings,
                        busy = profileBusy,
                        message = profileMessage,
                        onUnitsChanged = viewModel::setUnits,
                        onRouteBackupChanged = viewModel::setRouteBackup,
                        onDarkThemeChanged = viewModel::setDarkTheme,
                        onDeleteData = viewModel::deleteAllLocalData,
                        onUpdateProfile = onUpdateProfile,
                        onRootPageChanged = { profileIsRootPage = it },
                        onSignOut = onSignOut,
                    )
                }
            }
        }
    }

    if (showStartDialog) {
        StartWorkoutDialog(
            onDismiss = { showStartDialog = false },
            onDemo = { type ->
                showStartDialog = false
                viewModel.startDemo(type)
            },
            onReal = { type ->
                showStartDialog = false
                pendingRealType = type
                permissionLauncher.launch(
                    buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        add(Manifest.permission.ACTIVITY_RECOGNITION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray(),
                )
            },
        )
    }
}

@Composable
private fun StartWorkoutDialog(
    onDismiss: () -> Unit,
    onDemo: (WorkoutType) -> Unit,
    onReal: (WorkoutType) -> Unit,
) {
    var type by remember { mutableStateOf(WorkoutType.RUN) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Start workout", fontWeight = FontWeight.Bold) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Choose the activity, then use real sensors or the deterministic assessment trace.")
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { type = WorkoutType.WALK }) { Text(if (type == WorkoutType.WALK) "✓ Walk" else "Walk") }
                    TextButton(onClick = { type = WorkoutType.RUN }) { Text(if (type == WorkoutType.RUN) "✓ Run" else "Run") }
                }
                if (BuildConfig.DEBUG) {
                    Button(onClick = { onDemo(type) }) { Text("Run assessor demo") }
                }
                OutlinedButton(onClick = { onReal(type) }) { Text("Use real sensors") }
                Text(
                    "The demo injects a stationary start, walk, run, hill, pace decline and impossible GPS jump.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
