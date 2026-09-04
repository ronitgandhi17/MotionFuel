package com.ronitgandhi.motionfuel.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.ronitgandhi.motionfuel.ui.theme.FuelGreen
import java.io.File

@Composable
fun ProfileAvatar(photoUri: String?, name: String, modifier: Modifier = Modifier) {
    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = "Profile picture for $name",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape),
        )
    } else {
        Surface(modifier = modifier, shape = CircleShape, color = FuelGreen) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(name.trim().firstOrNull()?.uppercase() ?: "M", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun ProfilePhotoPicker(photoUri: String?, name: String, enabled: Boolean, onPhotoChanged: (String?) -> Unit) {
    val context = LocalContext.current
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var photoBeforeCamera by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        if (!captured) {
            capturedFile?.delete()
            capturedFile = null
            onPhotoChanged(photoBeforeCamera)
        }
        photoBeforeCamera = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            capturedFile?.delete()
            capturedFile = null
            onPhotoChanged(it.toString())
        }
    }
    DisposableEffect(Unit) {
        onDispose { capturedFile?.delete() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Profile picture", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ProfileAvatar(photoUri, name, Modifier.size(92.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = enabled,
                    onClick = {
                        capturedFile?.delete()
                        photoBeforeCamera = photoUri
                        val directory = File(context.cacheDir, "profile_photos").apply { mkdirs() }
                        val file = File.createTempFile("profile_camera_", ".jpg", directory)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        capturedFile = file
                        onPhotoChanged(uri.toString())
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Camera")
                }
                OutlinedButton(
                    enabled = enabled,
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Gallery")
                }
            }
        }
        if (photoUri != null) {
            TextButton(
                enabled = enabled,
                onClick = {
                    capturedFile?.delete()
                    capturedFile = null
                    onPhotoChanged(null)
                },
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Remove picture")
            }
        }
    }
}
