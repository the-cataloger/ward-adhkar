package org.ward.adhkar.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** The user's provided ornamental backdrop is bundled locally, never downloaded. */
object WardOrnament {
    fun load(context: Context): ImageBitmap? = runCatching {
        val encoded = context.assets.open("ward_ornament.webp.b64")
            .bufferedReader(Charsets.US_ASCII).use { it.readText() }
        val bytes = Base64.decode(encoded.trim(), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
