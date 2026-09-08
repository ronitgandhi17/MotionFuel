package com.ronitgandhi.motionfuel.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.ronitgandhi.motionfuel.R
import com.ronitgandhi.motionfuel.domain.algorithm.ActivityStateStabilizer
import com.ronitgandhi.motionfuel.domain.algorithm.EnergyEstimator
import com.ronitgandhi.motionfuel.domain.algorithm.GpsFilter
import com.ronitgandhi.motionfuel.domain.algorithm.SensorFusionClassifier
import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.LocationQuality
import com.ronitgandhi.motionfuel.domain.model.SensorFeatureWindow
import com.ronitgandhi.motionfuel.domain.model.WorkoutStatus
import com.ronitgandhi.motionfuel.domain.model.WorkoutTelemetry
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class WorkoutTrackingService : Service(), SensorEventListener, LocationListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gpsFilter = GpsFilter()
    private val classifier = SensorFusionClassifier()
    private val stabilizer = ActivityStateStabilizer()
    private val acceleration = ArrayDeque<Double>()
    private val rotation = ArrayDeque<Double>()

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var ticker: Job? = null
    private var startedAtElapsed = 0L
    private var pausedAtElapsed = 0L
    private var accumulatedPauseMillis = 0L
    private var stepBaseline: Float? = null
    private var latestStepCount = 0L
    private var recentStepCount = 0L
    private var recentStepTime = 0L
    private var currentAltitude: Double? = null
    private var lastSpeedMps = 0.0
    private var weightKg = 72.0
    private val autoPauseDetector = com.ronitgandhi.motionfuel.domain.algorithm.AutoPauseDetector()
    private var autoPauseEnabled = false
    private var adaptiveTracking = false
    private var automaticPause = false
    private var lastInterval = 1000L
    private var lastIntervalCheck = 0L
    private var nextAutomaticLap = 1000.0


    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startWorkout(
                runCatching { WorkoutType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: WorkoutType.RUN.name) }
                    .getOrDefault(WorkoutType.RUN),
                intent.getDoubleExtra(EXTRA_WEIGHT_KG, 72.0),
            )
            ACTION_PAUSE -> { automaticPause = false; pauseWorkout() }
            ACTION_LAP -> recordLap(false)
            ACTION_RESUME -> { automaticPause = false; autoPauseDetector.reset(); resumeWorkout() }
            ACTION_STOP -> stopWorkout()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startWorkout(type: WorkoutType, requestedWeightKg: Double) {
        if (WorkoutSessionController.telemetry.value.status == WorkoutStatus.ACTIVE) return
        val user = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        val options = user?.let { com.ronitgandhi.motionfuel.data.features.FeatureStore(this, it).data.value }
        autoPauseEnabled = options?.optBoolean("autoPause") ?: false
        adaptiveTracking = options?.optBoolean("adaptiveGps") ?: false
        automaticPause = false
        nextAutomaticLap = 1000.0
        autoPauseDetector.reset()
        weightKg = requestedWeightKg.coerceIn(35.0, 250.0)
        startedAtElapsed = SystemClock.elapsedRealtime()
        accumulatedPauseMillis = 0
        stepBaseline = null
        latestStepCount = 0
        recentStepCount = 0
        recentStepTime = startedAtElapsed
        currentAltitude = null
        lastSpeedMps = 0.0
        acceleration.clear()
        rotation.clear()
        gpsFilter.reset()
        stabilizer.reset()
        WorkoutSessionController.publish(WorkoutTelemetry(status = WorkoutStatus.ACTIVE, type = type))
        startForeground(NOTIFICATION_ID, notification("Starting ${type.name.lowercase()}…"))
        registerSensors()
        requestLocationUpdates()
        ticker?.cancel()
        ticker = serviceScope.launch {
            while (isActive) {
                delay(1_000)
                tick()
            }
        }
    }

    private fun pauseWorkout() {
        if (WorkoutSessionController.telemetry.value.status != WorkoutStatus.ACTIVE) return
        pausedAtElapsed = SystemClock.elapsedRealtime()
        WorkoutSessionController.update { it.copy(status = WorkoutStatus.PAUSED, autoPaused = automaticPause) }
    }

    private fun resumeWorkout() {
        if (WorkoutSessionController.telemetry.value.status != WorkoutStatus.PAUSED) return
        accumulatedPauseMillis += SystemClock.elapsedRealtime() - pausedAtElapsed
        gpsFilter.reset()
        WorkoutSessionController.update { it.copy(status = WorkoutStatus.ACTIVE, autoPaused = false) }
    }

    private fun stopWorkout() {
        ticker?.cancel()
        unregisterTracking()
        WorkoutSessionController.update { it.copy(status = WorkoutStatus.COMPLETE) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun tick() {
        val current = WorkoutSessionController.telemetry.value
        val now = SystemClock.elapsedRealtime()
        if (adaptiveTracking && now - lastIntervalCheck > 30_000) {
            lastIntervalCheck = now
            val battery = (getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val interval = com.ronitgandhi.motionfuel.domain.algorithm.TrainingAnalysis.locationInterval(battery, current.activity.type == ActivityType.STATIONARY, true)
            if (interval != lastInterval) {
                lastInterval = interval
                locationManager.removeUpdates(this)
                requestLocationUpdates()
            }
        }
        if (current.status != WorkoutStatus.ACTIVE) return
        val elapsedSeconds = ((now - startedAtElapsed - accumulatedPauseMillis) / 1_000L).coerceAtLeast(0)
        val cadence = if (now > recentStepTime) {
            (((latestStepCount - recentStepCount) * 60_000.0) / (now - recentStepTime)).toInt().coerceIn(0, 240)
        } else 0
        if (now - recentStepTime >= 5_000) {
            recentStepCount = latestStepCount
            recentStepTime = now
        }

        val features = SensorFeatureWindow(
            accelerationEnergy = acceleration.averageOrZero(),
            accelerationVariance = acceleration.variance(),
            gyroscopeVariance = rotation.variance(),
            stepRatePerMinute = cadence.toDouble(),
            gpsSpeedMps = lastSpeedMps,
            locationAccuracyMeters = when (current.gpsQuality) {
                LocationQuality.GOOD -> 8f
                LocationQuality.FAIR -> 25f
                LocationQuality.POOR -> 60f
            },
        )
        val activity = stabilizer.update(classifier.classify(features))
        val averagePace = if (current.distanceMeters > 20) elapsedSeconds / (current.distanceMeters / 1_000.0) else null
        WorkoutSessionController.publish(
            current.copy(
                elapsedSeconds = elapsedSeconds,
                currentPaceSecPerKm = if (lastSpeedMps > 0.4) 1_000.0 / lastSpeedMps else null,
                averagePaceSecPerKm = averagePace,
                steps = latestStepCount,
                cadenceSpm = cadence,
                caloriesKcal = EnergyEstimator.calories(weightKg, elapsedSeconds, activity.type),
                activity = activity,
            ),
        )
        if (current.distanceMeters >= nextAutomaticLap) {
            recordLap(true)
            nextAutomaticLap = (current.distanceMeters / 1000).toInt() * 1000.0 + 1000.0
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification("%.2f km • %s".format(current.distanceMeters / 1_000.0, activity.type.name.lowercase())))
    }

    override fun onLocationChanged(location: Location) {
        val current = WorkoutSessionController.telemetry.value
        val speed = if (location.hasSpeed()) location.speed.toDouble() else Double.NaN
        val fresh = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos in 0..10_000_000_000L
        if (autoPauseEnabled && fresh) {
            if (current.status == WorkoutStatus.ACTIVE && autoPauseDetector.shouldPause(speed, location.accuracy, SystemClock.elapsedRealtime())) {
                automaticPause = true
                pauseWorkout()
                return
            }
            if (current.status == WorkoutStatus.PAUSED && automaticPause && speed.isFinite() && speed >= 0.8 && location.accuracy in 0.1f..25f) {
                automaticPause = false
                autoPauseDetector.reset()
                resumeWorkout()
            }
        }
        if (WorkoutSessionController.telemetry.value.status != WorkoutStatus.ACTIVE) return
        val sample = GeoPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = if (location.hasAltitude()) location.altitude else currentAltitude,
            accuracyMeters = location.accuracy,
            timestampMillis = location.time,
        )
        val filtered = gpsFilter.evaluate(sample, current.activity.type)
        lastSpeedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0
        if (!filtered.accepted || filtered.point == null) {
            WorkoutSessionController.update {
                it.copy(gpsQuality = filtered.quality, rejectedGpsPoints = it.rejectedGpsPoints + 1)
            }
            return
        }
        val previousAltitude = current.route.lastOrNull()?.altitudeMeters
        val gain = if (previousAltitude != null && filtered.point.altitudeMeters != null) {
            (filtered.point.altitudeMeters - previousAltitude).coerceAtLeast(0.0)
        } else 0.0
        WorkoutSessionController.update {
            it.copy(
                distanceMeters = it.distanceMeters + filtered.distanceDeltaMeters,
                route = it.route + filtered.point,
                elevationGainMeters = it.elevationGainMeters + gain,
                gpsQuality = filtered.quality,
            )
        }
    }

    private fun recordLap(automatic: Boolean) {
        val current = WorkoutSessionController.telemetry.value
        val previous = current.laps.lastOrNull()
        if (current.status != WorkoutStatus.ACTIVE || current.distanceMeters <= (previous?.distanceMeters ?: 0.0)) return
        WorkoutSessionController.update { it.copy(laps = it.laps + com.ronitgandhi.motionfuel.domain.model.WorkoutLap(it.distanceMeters, it.elapsedSeconds, automatic)) }
    }

    @Deprecated("Legacy LocationListener callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) {
        WorkoutSessionController.update { it.copy(gpsQuality = LocationQuality.POOR) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val magnitude = sqrt(event.values.take(3).sumOf { it.toDouble() * it.toDouble() })
                acceleration.addBounded(abs(magnitude - SensorManager.GRAVITY_EARTH), 80)
            }
            Sensor.TYPE_GYROSCOPE -> {
                val magnitude = sqrt(event.values.take(3).sumOf { it.toDouble() * it.toDouble() })
                rotation.addBounded(magnitude, 80)
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val absolute = event.values.firstOrNull() ?: return
                if (stepBaseline == null) stepBaseline = absolute
                latestStepCount = (absolute - (stepBaseline ?: absolute)).toLong().coerceAtLeast(0)
            }
            Sensor.TYPE_PRESSURE -> {
                val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]).toDouble()
                currentAltitude = altitude
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerSensors() {
        listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_PRESSURE).forEach { type ->
            sensorManager.getDefaultSensor(type)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            WorkoutSessionController.update { it.copy(gpsQuality = LocationQuality.POOR) }
            return
        }
        // Combines satellite and network providers so the first usable point arrives faster indoors.
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        providers.forEach { provider ->
            runCatching {
                val interval = if (provider == LocationManager.GPS_PROVIDER) lastInterval else maxOf(lastInterval, 3_000L)
                locationManager.requestLocationUpdates(provider, interval, 0f, this, Looper.getMainLooper())
            }
        }
        // Seeds the map with the most accurate cached fix while fresh updates are being acquired.
        providers.mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .minByOrNull { it.accuracy }
            ?.let(::onLocationChanged)
        if (providers.isEmpty()) {
            WorkoutSessionController.update { state -> state.copy(gpsQuality = LocationQuality.POOR) }
        }
    }

    private fun unregisterTracking() {
        sensorManager.unregisterListener(this)
        runCatching { locationManager.removeUpdates(this) }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Active workout", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Keeps a MotionFuel workout recording in the background"
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("MotionFuel is tracking")
        .setContentText(text)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("MotionFuel is active")
                .setContentText("Open the app to view workout details")
                .build(),
        )
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        ticker?.cancel()
        unregisterTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ArrayDeque<Double>.addBounded(value: Double, maximum: Int) {
        addLast(value)
        while (size > maximum) removeFirst()
    }

    private fun ArrayDeque<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
    private fun ArrayDeque<Double>.variance(): Double {
        if (size < 2) return 0.0
        val mean = average()
        return sumOf { (it - mean) * (it - mean) } / size
    }

    companion object {
        const val ACTION_START = "com.ronitgandhi.motionfuel.START"
        const val ACTION_LAP = "com.ronitgandhi.motionfuel.LAP"
        const val ACTION_PAUSE = "com.ronitgandhi.motionfuel.PAUSE"
        const val ACTION_RESUME = "com.ronitgandhi.motionfuel.RESUME"
        const val ACTION_STOP = "com.ronitgandhi.motionfuel.STOP"
        const val EXTRA_TYPE = "workout_type"
        const val EXTRA_WEIGHT_KG = "weight_kg"
        private const val CHANNEL_ID = "active_workout"
        private const val NOTIFICATION_ID = 1001
    }
}
