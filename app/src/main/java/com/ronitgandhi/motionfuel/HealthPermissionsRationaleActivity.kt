package com.ronitgandhi.motionfuel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.ui.theme.MotionFuelTheme

/** Explains Health Connect use when Android or the Health Connect app requests a rationale. */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MotionFuelTheme(darkTheme = isSystemInDarkTheme()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("How MotionFuel uses health data", style = MaterialTheme.typography.headlineSmall)
                    Text("If you choose to connect Health Connect, MotionFuel reads steps and active energy to improve daily targets, weight for progress, and sleep and heart rate for recovery context.")
                    Text("Access is optional and read-only. MotionFuel does not write Health Connect records, use this data for advertising, or share it through social cards. You can revoke access from Health Connect at any time.")
                    Text("Without access, phone sensors and manual sleep/resting-heart-rate inputs remain available.")
                }
            }
        }
    }
}
