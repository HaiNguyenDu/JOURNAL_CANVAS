package com.example.journal_canvas.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitmapLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: BitmapMemoryCache,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun load(
        uriString: String,
        targetMaxDimensionPx: Int = BitmapMemoryCache.DEFAULT_TARGET_DIMENSION_PX,
    ): Bitmap? {
        if (cache.hasAtLeast(uriString, targetMaxDimensionPx)) {
            return cache.get(uriString, targetMaxDimensionPx)
        }

        return withContext(dispatchers.io) {
            val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            if (uri == null) {
                Log.w(TAG, "load: invalid URI string: $uriString")
                return@withContext null
            }
            val bounds = readBounds(uri)
            if (bounds == null) {
                Log.w(TAG, "load: readBounds failed for $uri")
                return@withContext null
            }
            val decodeMaxDimension = BitmapMemoryCache.bucketFor(targetMaxDimensionPx)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.first, bounds.second, decodeMaxDimension)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            } catch (e: Exception) {
                Log.w(TAG, "load: decode failed for $uri", e)
                null
            }
            if (bitmap == null) {
                Log.w(TAG, "load: decodeStream returned null for $uri (sampleSize=${opts.inSampleSize})")
            } else {
                cache.put(uriString, bitmap, decodeMaxDimension)
            }
            bitmap
        }
    }

    suspend fun loadDimensions(uriString: String): Size? = withContext(dispatchers.io) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext null
        readBounds(uri)?.let { Size(it.first, it.second) }
    }

    private fun readBounds(uri: Uri): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                Log.w(TAG, "readBounds: openInputStream returned null for $uri")
                return null
            }
            stream.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.w(TAG, "readBounds: failed to read header for $uri", e)
            return null
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            Log.w(TAG, "readBounds: invalid dimensions ${opts.outWidth}x${opts.outHeight} for $uri")
            return null
        }
        return opts.outWidth to opts.outHeight
    }

    private fun computeInSampleSize(srcWidth: Int, srcHeight: Int, maxDim: Int): Int {
        var sampleSize = 1
        var w = srcWidth
        var h = srcHeight
        while (w / 2 >= maxDim || h / 2 >= maxDim) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private companion object {
        const val TAG = "BitmapLoader"
    }
}
