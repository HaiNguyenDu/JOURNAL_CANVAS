package com.example.journal_canvas.util

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun delete(fileUriString: String): Boolean = withContext(dispatchers.io) {
        val canonicalDir = imagesDir().canonicalPath
        val canonicalFile = resolveInternalImagePath(fileUriString, canonicalDir)
            ?: return@withContext false
        File(canonicalFile).delete()
    }

    suspend fun deleteUnreferencedImages(referencedUriStrings: Set<String>): Int = withContext(dispatchers.io) {
        val dir = imagesDir()
        if (!dir.exists() || !dir.isDirectory) return@withContext 0

        val canonicalDir = dir.canonicalPath
        val referencedPaths = referencedUriStrings
            .mapNotNull { resolveInternalImagePath(it, canonicalDir) }
            .toSet()
        val sweepStartedAt = System.currentTimeMillis()

        dir.listFiles()
            ?.filter { it.isFile }
            ?.count { file ->
                val canonicalFile = runCatching { file.canonicalPath }.getOrNull()
                    ?: return@count false
                val isInternalImage = canonicalFile.startsWith(canonicalDir + File.separator)
                isInternalImage &&
                    file.lastModified() <= sweepStartedAt &&
                    canonicalFile !in referencedPaths &&
                    file.delete()
            }
            ?: 0
    }

    suspend fun importToInternalStorage(sourceUriString: String): String? = withContext(dispatchers.io) {
        val src = runCatching { Uri.parse(sourceUriString) }.getOrNull()
        if (src == null) {
            Log.w(TAG, "import: invalid source URI: $sourceUriString")
            return@withContext null
        }
        if (src.scheme == "file") return@withContext sourceUriString

        val dir = imagesDir().apply { if (!exists()) mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.bin")
        return@withContext try {
            val stream = context.contentResolver.openInputStream(src)
            if (stream == null) {
                Log.w(TAG, "import: openInputStream returned null for $src")
                return@withContext null
            }
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            Uri.fromFile(target).toString()
        } catch (e: Exception) {
            Log.w(TAG, "import: copy failed for $src", e)
            target.delete()
            null
        }
    }

    private fun imagesDir(): File = File(context.filesDir, IMAGES_DIR)

    private fun resolveInternalImagePath(uriString: String, canonicalDir: String): String? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        val canonicalFile = runCatching { File(path).canonicalPath }.getOrNull() ?: return null
        if (!canonicalFile.startsWith(canonicalDir + File.separator)) {
            Log.w(TAG, "refusing to resolve file outside $canonicalDir: $canonicalFile")
            return null
        }
        return canonicalFile
    }

    private companion object {
        const val IMAGES_DIR = "canvas_images"
        const val TAG = "ImageStore"
    }
}
