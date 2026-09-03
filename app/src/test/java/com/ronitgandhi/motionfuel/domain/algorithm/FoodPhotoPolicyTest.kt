package com.ronitgandhi.motionfuel.domain.algorithm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodPhotoPolicyTest {
    @Test
    fun `accepts Android content URI`() {
        val uri = "content://com.android.providers.media.documents/document/image%3A42"
        assertEquals(uri, FoodPhotoPolicy.sanitize(uri))
    }

    @Test
    fun `rejects file and remote URI schemes`() {
        assertNull(FoodPhotoPolicy.sanitize("file:///sdcard/food.jpg"))
        assertNull(FoodPhotoPolicy.sanitize("https://example.com/food.jpg"))
    }

    @Test
    fun `rejects oversized and malformed values`() {
        assertNull(FoodPhotoPolicy.sanitize("content://${"a".repeat(2_050)}"))
        assertNull(FoodPhotoPolicy.sanitize("not a uri"))
    }
}
