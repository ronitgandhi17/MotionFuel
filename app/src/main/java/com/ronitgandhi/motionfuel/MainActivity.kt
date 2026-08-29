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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ronitgandhi.motionfuel.auth.AuthLifecycle
import com.ronitgandhi.motionfuel.auth.ClerkAuthViewModel
import com.ronitgandhi.motionfuel.domain.model.WorkoutStatus
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import com.ronitgandhi.motionfuel.membership.MembershipViewModel
import com.ronitgandhi.motionfuel.ui.screens.ActivityScreen
import com.ronitgandhi.motionfuel.ui.screens.AuthenticationLoadingScreen
import com.ronitgandhi.motionfuel.ui.screens.ClerkAuthScreen
import com.ronitgandhi.motionfuel.ui.screens.ClerkConfigurationRequiredScreen
import com.ronitgandhi.motionfuel.ui.screens.FoodScreen
import com.ronitgandhi.motionfuel.ui.screens.InsightsScreen
import com.ronitgandhi.motionfuel.ui.screens.ProfileScreen
import com.ronitgandhi.motionfuel.ui.screens.TodayScreen
import com.ronitgandhi.motionfuel.ui.screens.WorkoutScreen
import com.ronitgandhi.motionfuel.ui.theme.MotionFuelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Gates every dashboard route behind the current Clerk authentication state.
            val authViewModel: ClerkAuthViewModel = viewModel()
            val auth by authViewModel.state.collectAsStateWithLifecycle()
            when (auth.lifecycle) {
                AuthLifecycle.CONFIGURATION_REQUIRED -> MotionFuelTheme(darkTheme = true) {
                    ClerkConfigurationRequiredScreen()
                }
                AuthLifecycle.LOADING -> MotionFuelTheme(darkTheme = true) {
                    AuthenticationLoadingScreen()
                }
                AuthLifecycle.SIGNED_OUT -> MotionFuelTheme(darkTheme = true) {
                    ClerkAuthScreen(
                        state = auth,
                        onModeChanged = authViewModel::selectMode,
                        onSignIn = authViewModel::signIn,
                        onSignUp = authViewModel::signUp,
                        onVerifyEmail = authViewModel::verifyEmail,
                    )
                }
                AuthLifecycle.SIGNED_IN -> {
                    // Creates app and membership state only after Clerk confirms a signed-in user.
                    val motionFuelViewModel: MotionFuelViewModel = viewModel()
                    val membershipViewModel: MembershipViewModel = viewModel()
                    val settings by motionFuelViewModel.settings.collectAsStateWithLifecycle()
                    MotionFuelTheme(darkTheme = settings.darkTheme) {
                        MotionFuelRoot(
                            viewModel = motionFuelViewModel,
                            membershipViewModel = membershipViewModel,
                            profileName = auth.displayName,
                            profileEmail = auth.emailAddress,
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
    INSIGHTS("Insights", Icons.Rounded.AutoAwesome),
    PROFILE("Profile", Icons.Rounded.Person),
}

@Composable
private fun MotionFuelRoot(
    viewModel: MotionFuelViewModel,
    membershipViewModel: MembershipViewModel,
    profileName: String,
    profileEmail: String?,
    onSignOut: () -> Unit,
) {
    // Collects each offline-first data stream as lifecycle-aware Compose state.
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val totals by viewModel.nutritionTotals.collectAsStateWithLifecycle()
    val entries by viewModel.nutritionEntries.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val weatherStatus by viewModel.weatherStatus.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val foodResults by viewModel.foodResults.collectAsStateWithLifecycle()
    val foodSearchStatus by viewModel.foodSearchStatus.collectAsStateWithLifecycle()
    val membership by membershipViewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(MainTab.TODAY) }
    var showStartDialog by remember { mutableStateOf(false) }
    var pendingRealType by remember { mutableStateOf<WorkoutType?>(null) }

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
            onPauseResume = viewModel::pauseOrResumeWorkout,
            onFinish = viewModel::finishWorkout,
            onDone = {
                viewModel.dismissCompletedWorkout()
                selectedTab = MainTab.ACTIVITY
            },
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selectedTab) {
                    MainTab.TODAY -> TodayScreen(
                        workouts = workouts,
                        nutrition = totals,
                        weather = weather,
                        weatherStatus = weatherStatus,
                        insights = insights,
                        settings = settings,
                        onStartWorkout = { showStartDialog = true },
                        onRefreshWeather = viewModel::refreshWeather,
                        onOpenActivity = { selectedTab = MainTab.ACTIVITY },
                        onOpenFood = { selectedTab = MainTab.FOOD },
                    )
                    MainTab.ACTIVITY -> ActivityScreen(workouts, settings) { showStartDialog = true }
                    MainTab.FOOD -> FoodScreen(
                        totals = totals,
                        entries = entries,
                        results = foodResults,
                        searchStatus = foodSearchStatus,
                        onSearch = viewModel::searchFoods,
                        onAddFood = { viewModel.addFood(it) },
                        onAddManual = viewModel::addManualFood,
                    )
                    MainTab.INSIGHTS -> InsightsScreen(insights)
                    MainTab.PROFILE -> ProfileScreen(
                        profileName = profileName,
                        profileEmail = profileEmail,
                        settings = settings,
                        latestRoute = workouts.firstOrNull()?.route ?: emptyList(),
                        membership = membership,
                        onUnitsChanged = viewModel::setUnits,
                        onRouteBackupChanged = viewModel::setRouteBackup,
                        onDarkThemeChanged = viewModel::setDarkTheme,
                        onDeleteData = viewModel::deleteAllLocalData,
                        onRefreshMembership = membershipViewModel::refresh,
                        onSubscribe = membershipViewModel::startSubscription,
                        onManageMembership = membershipViewModel::openBillingPortal,
                        onPaymentSheetPresented = membershipViewModel::paymentSheetPresented,
                        onPaymentCompleted = membershipViewModel::paymentCompleted,
                        onPaymentFailed = membershipViewModel::paymentFailed,
                        onPortalOpened = membershipViewModel::portalOpened,
                        onSignOut = onSignOut,
                    )
                }
            }
        }
    }

    if (showStartDialog) {
        StartWorkoutDialog(
            onDismiss = { showStartDialog = false },
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
    onReal: (WorkoutType) -> Unit,
) {
    var type by remember { mutableStateOf(WorkoutType.RUN) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Start workout", fontWeight = FontWeight.Bold) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Choose the activity, then start tracking with your device sensors and GPS.")
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { type = WorkoutType.WALK }) { Text(if (type == WorkoutType.WALK) "✓ Walk" else "Walk") }
                    TextButton(onClick = { type = WorkoutType.RUN }) { Text(if (type == WorkoutType.RUN) "✓ Run" else "Run") }
                }
                OutlinedButton(onClick = { onReal(type) }) { Text("Start tracking") }
                Text(
                    "MotionFuel records real GPS, motion and elevation in a foreground service. Grant location to begin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
