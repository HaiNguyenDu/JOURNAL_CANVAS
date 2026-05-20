package com.example.journal_canvas.util

import android.graphics.Bitmap
import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitmapMemoryCache @Inject constructor() {
    private val cache = object : LruCache<CacheKey, Bitmap>(maxCacheSize()) {
        override fun sizeOf(key: CacheKey, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    private val bucketsByUri = mutableMapOf<String, MutableSet<Int>>()
    private val pinnedUris = mutableSetOf<String>()
    private val pinnedBitmaps = mutableMapOf<CacheKey, Bitmap>()

    @Synchronized
    fun get(uri: String): Bitmap? {
        return get(uri, MIN_TARGET_DIMENSION_PX)
    }

    @Synchronized
    fun get(uri: String, requestedMaxDimensionPx: Int): Bitmap? {
        val requestedBucket = bucketFor(requestedMaxDimensionPx)
        val exactOrLarger = findBestKey(uri, requestedBucket, requireAtLeast = true)
        if (exactOrLarger != null) return getValidBitmap(exactOrLarger)

        val fallback = findBestKey(uri, requestedBucket, requireAtLeast = false)
        return fallback?.let { getValidBitmap(it) }
    }

    @Synchronized
    fun hasAtLeast(uri: String, requestedMaxDimensionPx: Int): Boolean {
        val requestedBucket = bucketFor(requestedMaxDimensionPx)
        val key = findBestKey(uri, requestedBucket, requireAtLeast = true)
        return key != null && getValidBitmap(key) != null
    }

    @Synchronized
    fun put(
        uri: String,
        bitmap: Bitmap,
        requestedMaxDimensionPx: Int = DEFAULT_TARGET_DIMENSION_PX,
    ) {
        val key = CacheKey(uri = uri, bucket = bucketFor(requestedMaxDimensionPx))
        bucketsByUri.getOrPut(uri) { mutableSetOf() }.add(key.bucket)
        cache.put(key, bitmap)
        if (uri in pinnedUris) {
            pinnedBitmaps[key] = bitmap
        }
    }

    @Synchronized
    fun pinUris(uris: Set<String>) {
        pinnedUris.clear()
        pinnedUris.addAll(uris)

        val pinnedIterator = pinnedBitmaps.iterator()
        while (pinnedIterator.hasNext()) {
            val entry = pinnedIterator.next()
            if (entry.key.uri !in pinnedUris || entry.value.isRecycled) {
                pinnedIterator.remove()
            }
        }

        uris.forEach { uri ->
            val buckets = bucketsByUri[uri] ?: return@forEach
            buckets.forEach { bucket ->
                val key = CacheKey(uri, bucket)
                val bitmap = cache.get(key)
                if (bitmap != null && !bitmap.isRecycled) {
                    pinnedBitmaps[key] = bitmap
                }
            }
        }
    }

    @Synchronized
    fun clearPins() {
        pinnedUris.clear()
        pinnedBitmaps.clear()
    }

    private fun findBestKey(
        uri: String,
        requestedBucket: Int,
        requireAtLeast: Boolean,
    ): CacheKey? {
        val buckets = bucketsByUri[uri] ?: return null
        var bestLargerOrEqual = Int.MAX_VALUE
        var bestSmaller = 0

        val iterator = buckets.iterator()
        while (iterator.hasNext()) {
            val bucket = iterator.next()
            val key = CacheKey(uri, bucket)
            if (getValidBitmap(key, pruneBucket = false) == null) {
                iterator.remove()
                continue
            }

            if (bucket >= requestedBucket && bucket < bestLargerOrEqual) {
                bestLargerOrEqual = bucket
            } else if (!requireAtLeast && bucket < requestedBucket && bucket > bestSmaller) {
                bestSmaller = bucket
            }
        }

        return when {
            bestLargerOrEqual != Int.MAX_VALUE -> CacheKey(uri, bestLargerOrEqual)
            !requireAtLeast && bestSmaller > 0 -> CacheKey(uri, bestSmaller)
            else -> null
        }
    }

    private fun getValidBitmap(key: CacheKey, pruneBucket: Boolean = true): Bitmap? {
        val pinned = pinnedBitmaps[key]
        if (pinned != null) {
            if (!pinned.isRecycled) return pinned
            pinnedBitmaps.remove(key)
        }

        val cached = cache.get(key)
        if (cached != null && !cached.isRecycled) return cached

        if (pruneBucket) {
            bucketsByUri[key.uri]?.remove(key.bucket)
        }
        return null
    }

    private fun maxCacheSize(): Int {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return maxMemoryKb / 8
    }

    private data class CacheKey(
        val uri: String,
        val bucket: Int,
    )

    companion object {
        const val MIN_TARGET_DIMENSION_PX = 128
        const val DEFAULT_TARGET_DIMENSION_PX = 512
        const val MAX_TARGET_DIMENSION_PX = 2048

        fun bucketFor(requestedMaxDimensionPx: Int): Int {
            val target = requestedMaxDimensionPx.coerceIn(
                MIN_TARGET_DIMENSION_PX,
                MAX_TARGET_DIMENSION_PX,
            )
            var bucket = MIN_TARGET_DIMENSION_PX
            while (bucket < target && bucket < MAX_TARGET_DIMENSION_PX) {
                bucket *= 2
            }
            return bucket.coerceAtMost(MAX_TARGET_DIMENSION_PX)
        }
    }
}
