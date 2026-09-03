package com.ronitgandhi.motionfuel.domain.algorithm

import java.net.URI

object FoodPhotoPolicy {
    // Keeps only bounded Android content references so file and network paths cannot be persisted as food photos.
    fun sanitize(uri: String?): String? = uri
        ?.takeIf { it.length <= 2_048 }
        ?.takeIf { runCatching { URI(it).scheme.equals("content", ignoreCase = true) }.getOrDefault(false) }
}
