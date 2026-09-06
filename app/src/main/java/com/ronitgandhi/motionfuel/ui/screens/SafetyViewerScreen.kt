package com.ronitgandhi.motionfuel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.ui.components.RouteMap

@Composable
fun SafetyViewerScreen(token: String, onBack: () -> Unit) {
    var status by remember { mutableStateOf("Opening safety share…") }
    var point by remember { mutableStateOf<GeoPoint?>(null) }
    DisposableEffect(token) {
        val registration = FirebaseFirestore.getInstance().collection("safetyShares").document(token).addSnapshotListener { document, error ->
            if (error != null || document == null || !document.exists()) status = "This safety link is invalid or unavailable."
            else {
                val active = document.getBoolean("active") == true
                val expires = document.getLong("expiresAtMillis") ?: 0L
                status = if (!active || expires < System.currentTimeMillis()) "This safety share has ended." else "Live location • updates while the workout is active"
                val lat = document.getDouble("latitude"); val lon = document.getDouble("longitude")
                if (lat != null && lon != null) point = GeoPoint(lat, lon, timestampMillis = document.getLong("updatedAtMillis") ?: System.currentTimeMillis())
            }
        }
        onDispose { registration.remove() }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }; Text("Safety share", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Icon(Icons.Rounded.Security, null); Text(status); point?.let { RouteMap(listOf(it), Modifier.fillMaxWidth().height(300.dp).padding(top = 12.dp)) } } }
    }
}
