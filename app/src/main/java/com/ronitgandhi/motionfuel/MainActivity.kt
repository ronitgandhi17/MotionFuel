package com.ronitgandhi.motionfuel

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
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
import com.ronitgandhi.motionfuel.domain.model.WorkoutStatus
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import com.ronitgandhi.motionfuel.ui.screens.ActivityScreen
import com.ronitgandhi.motionfuel.ui.screens.AuthFlow
import com.ronitgandhi.motionfuel.ui.screens.FirebaseConfigurationRequiredScreen
import com.ronitgandhi.motionfuel.ui.screens.FoodScreen
import com.ronitgandhi.motionfuel.ui.screens.ProfileScreen
import com.ronitgandhi.motionfuel.ui.screens.ProfileSetupGate
import com.ronitgandhi.motionfuel.ui.screens.ProgressScreen
import com.ronitgandhi.motionfuel.ui.screens.TodayScreen
import com.ronitgandhi.motionfuel.ui.screens.WorkoutScreen
import com.ronitgandhi.motionfuel.ui.theme.MotionFuelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MotionFuelViewModel = viewModel()
            // Without google-services.json there is no Firebase; show a configuration gate.
            if (!viewModel.firebaseConfigured) {
                MotionFuelTheme(darkTheme = true) { FirebaseConfigurationRequiredScreen() }
                return@setContent
            }
            val user by viewModel.authUser.collectAsStateWithLifecycle()
            val profile by viewModel.profile.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            MotionFuelTheme(darkTheme = settings.darkTheme) {
                val currentUser = user
                val currentProfile = profile
                when {
                    currentUser == null -> AuthFlow(viewModel)
                    currentProfile == null || !currentProfile.profileComplete ->
                        ProfileSetupGate(viewModel = viewModel, email = currentUser.email)
                    else -> MotionFuelRoot(viewModel = viewModel, onSignOut = viewModel::signOut)
                }
            }
        }
    }
}

private enum class MainTab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.Home),
    ACTIVITY("Activity", Icons.Rounded.DirectionsRun),
    FOOD("Food", Icons.Rounded.Restaurant),
    PROGRESS("Progress", Icons.Rounded.Insights),
    PROFILE("Profile", Icons.Rounded.Person),
}

@Composable
private fun MotionFuelRoot(
    viewModel: MotionFuelViewModel,
    onSignOut: () -> Unit,
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val totals by viewModel.nutritionTotals.collectAsStateWithLifecycle()
    val entries by viewModel.nutritionEntries.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val weatherStatus by viewModel.weatherStatus.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val foodResults by viewModel.foodResults.collectAsStateWithLifecycle()
    val foodSearchStatus by viewModel.foodSearchStatus.collectAsStateWithLifecycle()
    val customMeals by viewModel.customMeals.collectAsStateWithLifecycle()
    val weights by viewModel.weights.collectAsStateWithLifecycle()
    val dailySummaries by viewModel.dailySummaries.collectAsStateWithLifecycle()
    val maintenanceSnapshots by viewModel.maintenanceSnapshots.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(MainTab.TODAY) }
    var showStartDialog by remember { mutableStateOf(false) }
    var pendingRealType by remember { mutableStateOf<WorkoutType?>(null) }

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
                        profile = profile,
                        totals = totals,
                        entries = entries,
                        workouts = workouts,
                        weather = weather,
                        weatherStatus = weatherStatus,
                        insights = insights,
                        settings = settings,
                        onStartWorkout = { showStartDialog = true },
                        onRefreshWeather = viewModel::refreshWeather,
                        onOpenFood = { selectedTab = MainTab.FOOD },
                        onOpenProgress = { selectedTab = MainTab.PROGRESS },
                    )
                    MainTab.ACTIVITY -> ActivityScreen(workouts, settings) { showStartDialog = true }
                    MainTab.FOOD -> FoodScreen(
                        profile = profile,
                        totals = totals,
                        entries = entries,
                        customMeals = customMeals,
                        results = foodResults,
                        searchStatus = foodSearchStatus,
                        onSearch = viewModel::searchFoods,
                        onClearSearch = viewModel::clearFoodResults,
                        onAddFood = viewModel::addFood,
                        onAddManual = viewModel::addManualFood,
                        onLogCustomMeal = viewModel::logCustomMeal,
                        onSaveCustomMeal = viewModel::saveCustomMeal,
                        onDeleteCustomMeal = viewModel::deleteCustomMeal,
                        onDeleteEntry = viewModel::deleteFood,
                    )
                    MainTab.PROGRESS -> ProgressScreen(
                        profile = profile,
                        weights = weights,
                        dailySummaries = dailySummaries,
                        maintenanceSnapshots = maintenanceSnapshots,
                        insights = insights,
                        settings = settings,
                        onAddWeight = viewModel::addWeight,
                        onUpdateGoal = viewModel::updateDailyGoal,
                        onUpdateActivity = viewModel::updateActivityLevel,
                    )
                    MainTab.PROFILE -> ProfileScreen(
                        profile = profile,
                        settings = settings,
                        latestRoute = workouts.firstOrNull()?.route ?: emptyList(),
                        onUnitsChanged = viewModel::setUnits,
                        onRouteBackupChanged = viewModel::setRouteBackup,
                        onDarkThemeChanged = viewModel::setDarkTheme,
                        onEditProfile = viewModel::editProfileBasics,
                        onDeleteData = viewModel::deleteAllLocalData,
                        onDeleteAccount = { viewModel.deleteAccount { } },
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
            Column {
                Text("Choose the activity, then start tracking with your device sensors and GPS.")
                Row {
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