package com.ronitgandhi.motionfuel.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SyncDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.domain.algorithm.PrivacyZone
import com.ronitgandhi.motionfuel.domain.algorithm.PrivacyZoneMasker
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.Insight
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.UserSettings
import com.ronitgandhi.motionfuel.domain.model.WeatherContext
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.membership.MembershipUiState
import com.ronitgandhi.motionfuel.ui.components.BrandMark
import com.ronitgandhi.motionfuel.ui.components.EmptyInsightCard
import com.ronitgandhi.motionfuel.ui.components.InsightCard
import com.ronitgandhi.motionfuel.ui.components.MacroProgress
import com.ronitgandhi.motionfuel.ui.components.MetricCard
import com.ronitgandhi.motionfuel.ui.components.RouteCanvas
import com.ronitgandhi.motionfuel.ui.components.SectionHeader
import com.ronitgandhi.motionfuel.ui.components.formatDistance
import com.ronitgandhi.motionfuel.ui.components.formatDuration
import com.ronitgandhi.motionfuel.ui.components.formatPace
import com.ronitgandhi.motionfuel.ui.theme.FuelGreen
import com.ronitgandhi.motionfuel.ui.theme.FuelOrange
import com.ronitgandhi.motionfuel.ui.theme.FuelRose
import com.ronitgandhi.motionfuel.ui.theme.FuelSky
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TodayScreen(
    workouts: List<WorkoutSummary>,
    nutrition: NutritionTotals,
    weather: WeatherContext,
    weatherStatus: String,
    insights: List<Insight>,
    settings: UserSettings,
    onStartWorkout: () -> Unit,
    onRefreshWeather: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenFood: () -> Unit,
) {
    val lastWorkout = workouts.firstOrNull()
    val activeMinutes = workouts.filter { isToday(it.startedAtMillis) }.sumOf { it.durationSeconds } / 60
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Good afternoon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Move with context", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    IconButton(onClick = onRefreshWeather) { Icon(Icons.Rounded.Cloud, contentDescription = "Refresh weather") }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(28.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MELBOURNE NOW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("${weather.temperatureC.toInt()}°", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                        Text("${weather.humidityPercent}% humidity • ${weather.windSpeedKph.toInt()} km/h wind")
                        Text(weatherStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.Cloud, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Active today", "$activeMinutes min", "Goal 45 min", FuelGreen, Modifier.weight(1f))
                MetricCard(
                    "Last activity",
                    lastWorkout?.let { formatDistance(it.distanceMeters, settings.units == UnitSystem.IMPERIAL) } ?: "No run yet",
                    lastWorkout?.let { formatDuration(it.durationSeconds) } ?: "Start below",
                    FuelSky,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            SectionHeader("Adaptive insight")
            Spacer(Modifier.height(10.dp))
            insights.firstOrNull()?.let { InsightCard(it) } ?: EmptyInsightCard()
        }
        item {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    SectionHeader("Today’s fuel", action = "Open food", onAction = onOpenFood)
                    MacroProgress("Energy", nutrition.caloriesKcal, 2_200.0, FuelGreen, "kcal")
                    MacroProgress("Protein", nutrition.proteinG, 100.0, FuelSky)
                    MacroProgress("Carbohydrate", nutrition.carbohydratesG, 260.0, FuelOrange)
                }
            }
        }
        item {
            Button(
                onClick = onStartWorkout,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Start walk or run", fontWeight = FontWeight.Bold)
            }
            if (lastWorkout != null) {
                TextButton(onClick = onOpenActivity, modifier = Modifier.fillMaxWidth()) { Text("Review activity history") }
            }
        }
    }
}

@Composable
fun ActivityScreen(workouts: List<WorkoutSummary>, settings: UserSettings, onStartWorkout: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Activity", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Room-backed history loads instantly, even offline.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            FilledTonalButton(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Rounded.DirectionsRun, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Track a new activity")
            }
        }
        if (workouts.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.DirectionsRun, contentDescription = null, modifier = Modifier.size(44.dp), tint = FuelGreen)
                        Spacer(Modifier.height(12.dp))
                        Text("No saved workouts", fontWeight = FontWeight.Bold)
                        Text("Use the assessor demo for a deterministic first trace.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            items(workouts, key = { it.id }) { workout ->
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = FuelGreen.copy(alpha = 0.15f)) {
                                Icon(Icons.Rounded.DirectionsRun, contentDescription = null, tint = FuelGreen, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(workout.type.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
                                Text(
                                    SimpleDateFormat("EEE, d MMM • h:mm a", Locale.getDefault()).format(Date(workout.startedAtMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(formatDistance(workout.distanceMeters, settings.units == UnitSystem.IMPERIAL), fontWeight = FontWeight.ExtraBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            SmallMetric("Time", formatDuration(workout.durationSeconds))
                            SmallMetric("Pace", "${formatPace(workout.averagePaceSecPerKm)} /km")
                            SmallMetric("Climb", "${workout.elevationGainMeters.toInt()} m")
                        }
                        if (workout.route.isNotEmpty()) {
                            RouteCanvas(workout.route, Modifier.fillMaxWidth().height(130.dp), workout.rejectedGpsPoints > 0)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallMetric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InsightsScreen(insights: List<Insight>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Insights", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Transparent recommendations ranked by evidence and confidence.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (insights.isEmpty()) item { EmptyInsightCard() } else items(insights, key = { it.id }) { InsightCard(it) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = FuelGreen)
                    Spacer(Modifier.size(12.dp))
                    Text("AFEE uses bounded rules and personal context. It does not diagnose or prescribe treatment.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    profileName: String,
    profileEmail: String?,
    settings: UserSettings,
    membership: MembershipUiState,
    onUnitsChanged: (UnitSystem) -> Unit,
    onRouteBackupChanged: (Boolean) -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
    onDeleteData: () -> Unit,
    onRefreshMembership: () -> Unit,
    onSubscribe: () -> Unit,
    onManageMembership: () -> Unit,
    onPaymentSheetPresented: () -> Unit,
    onPaymentCompleted: () -> Unit,
    onPaymentFailed: (String) -> Unit,
    onPortalOpened: () -> Unit,
    onSignOut: () -> Unit,
) {
    // Builds the local privacy preview independently of the saved workout route.
    var confirmDelete by remember { mutableStateOf(false) }
    val originalRoute = remember { privacyRoute() }
    val zone = remember { PrivacyZone(originalRoute.first(), 95.0) }
    val masked = remember { PrivacyZoneMasker.mask(originalRoute, listOf(zone)) }
    val context = LocalContext.current
    // Maps every Stripe PaymentSheet result back into the membership ViewModel.
    val paymentSheet = rememberPaymentSheet { result ->
        when (result) {
            is PaymentSheetResult.Completed -> onPaymentCompleted()
            is PaymentSheetResult.Canceled -> onPaymentFailed("Checkout canceled.")
            is PaymentSheetResult.Failed -> onPaymentFailed(
                result.error.localizedMessage ?: "Stripe could not complete the payment.",
            )
        }
    }

    // Presents PaymentSheet once when the server returns short-lived subscription secrets.
    LaunchedEffect(membership.payment) {
        membership.payment?.let { payment ->
            onPaymentSheetPresented()
            paymentSheet.presentWithPaymentIntent(
                payment.clientSecret,
                PaymentSheet.Configuration(
                    merchantDisplayName = "MotionFuel",
                    primaryButtonLabel = "Start Pro membership",
                    customer = PaymentSheet.CustomerConfiguration(
                        id = payment.customerId,
                        ephemeralKeySecret = payment.ephemeralKey,
                    ),
                ),
            )
        }
    }
    // Opens the one-time Stripe Customer Portal URL inside a secure Custom Tab.
    LaunchedEffect(membership.portalUrl) {
        membership.portalUrl?.let { url ->
            onPortalOpened()
            runCatching {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            }.onFailure {
                onPaymentFailed("No browser is available to open Stripe billing.")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(Modifier.size(52.dp))
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(profileName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(
                        profileEmail ?: "Signed in with Clerk",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SettingsCard(title = "Membership", icon = Icons.Rounded.CreditCard) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(membership.statusLabel, fontWeight = FontWeight.Bold)
                        Text(
                            membership.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (membership.busy) CircularProgressIndicator(Modifier.size(24.dp))
                }
                if (!membership.configured) {
                    Text(
                        "Add the Stripe publishable key and secure membership API URL to enable billing.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (!membership.canManageBilling) {
                    Button(
                        onClick = onSubscribe,
                        enabled = !membership.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Join MotionFuel Pro") }
                } else {
                    OutlinedButton(
                        onClick = onManageMembership,
                        enabled = !membership.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Manage billing with Stripe") }
                }
                TextButton(onClick = onRefreshMembership, enabled = !membership.busy) {
                    Text("Refresh membership status")
                }
                membership.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Membership access is read from Stripe after your Clerk session is verified by the server.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingsCard(title = "Privacy & cloud", icon = Icons.Rounded.Lock) {
                SettingToggle(
                    title = "Back up detailed routes",
                    detail = if (settings.routeBackupEnabled) "Masked route may sync" else "Detailed routes remain local",
                    checked = settings.routeBackupEnabled,
                    onCheckedChange = onRouteBackupChanged,
                )
                RouteCanvas(masked, Modifier.fillMaxWidth().height(135.dp))
                Text("Privacy-zone preview: ${originalRoute.size - masked.size} endpoint points removed inside 95 m.", style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            SettingsCard(title = "Display", icon = Icons.Rounded.DarkMode) {
                SettingToggle("Dark theme", "Applies immediately", settings.darkTheme, onDarkThemeChanged)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onUnitsChanged(UnitSystem.METRIC) }, enabled = settings.units != UnitSystem.METRIC, modifier = Modifier.weight(1f)) { Text("Metric") }
                    OutlinedButton(onClick = { onUnitsChanged(UnitSystem.IMPERIAL) }, enabled = settings.units != UnitSystem.IMPERIAL, modifier = Modifier.weight(1f)) { Text("Imperial") }
                }
            }
        }
        item {
            SettingsCard(title = "Sensor readiness", icon = Icons.Rounded.Sensors) {
                ReadinessRow(Icons.Rounded.MyLocation, "GPS + route filtering", "Required for live route", FuelGreen)
                ReadinessRow(Icons.Rounded.Sensors, "Motion fusion", "Accelerometer, gyro, steps", FuelSky)
                ReadinessRow(Icons.Rounded.SyncDisabled, "Offline-first", "Room remains the source of truth", FuelOrange)
            }
        }
        item {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = FuelRose)
                Spacer(Modifier.size(8.dp))
                Text("Delete local demo data", color = FuelRose)
            }
        }
        item {
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Sign out of Clerk")
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete local data?") },
            text = { Text("Saved workouts and nutrition entries on this device will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = { onDeleteData(); confirmDelete = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ReadinessRow(icon: ImageVector, title: String, detail: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.13f)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp).size(18.dp))
        }
        Spacer(Modifier.size(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun isToday(time: Long): Boolean {
    val formatter = SimpleDateFormat("yyyyMMdd", Locale.US)
    return formatter.format(Date(time)) == formatter.format(Date())
}

private fun privacyRoute(): List<GeoPoint> {
    val now = System.currentTimeMillis()
    return List(18) { index ->
        GeoPoint(-37.8136 + index * 0.00008, 144.9631 + index * 0.00009, timestampMillis = now + index * 5_000)
    }
}
