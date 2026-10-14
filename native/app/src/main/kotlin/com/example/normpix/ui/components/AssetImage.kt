package com.example.normpix.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pictures decoded from the app's bundled assets (the 3D category pictures, the brand logos), shared app-wide. Each is small
 * (128-160 px) but a picker shows hundreds, so they are decoded off the main thread as they come into view and held in a
 * cache of bounded size, not all kept at once.
 */
internal object AssetBitmaps {
    private val cache = object : LruCache<String, ImageBitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Paths that are missing or won't decode, so a broken one isn't tried again on every frame. */
    private val broken = ConcurrentHashMap.newKeySet<String>()

    fun cached(path: String): ImageBitmap? = cache.get(path)

    /** The picture at [path], decoding it if it isn't cached; null when it is missing or broken. Blocking: call off the main thread. */
    fun load(context: Context, path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        if (path in broken) return null
        val bitmap = runCatching { context.assets.open(path).use(BitmapFactory::decodeStream) }.getOrNull()
        if (bitmap == null) {
            broken += path
            return null
        }
        // Lists draw these at a third of their size or less; mipmaps keep them smooth there instead of grainy.
        bitmap.setHasMipMap(true)
        bitmap.prepareToDraw()
        return bitmap.asImageBitmap().also { cache.put(path, it) }
    }

    /** A sixteenth of the app's memory, at most 16 MB: some 250 pictures, a picker's worth several times over. */
    private fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 16).coerceAtMost(16L * 1024 * 1024).toInt()
}

/**
 * A bundled picture ([path] under `assets/`), fitted whole into [modifier]'s box. Until it has been decoded - only the first
 * time it is shown - the box stays empty, and so does it for a picture that can't be loaded. Decorative: no description.
 */
@Composable
fun AssetImage(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(path) { mutableStateOf(AssetBitmaps.cached(path)) }
    LaunchedEffect(path) {
        if (bitmap == null) bitmap = withContext(Dispatchers.IO) { AssetBitmaps.load(context, path) }
    }
    val picture = bitmap
    if (picture == null) {
        Box(modifier)
    } else {
        Image(picture, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
    }
}
