package com.example.myapplication

import com.example.myapplication.ui.UiText

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Keep a bounded, oriented copy so photos remain available offline after picker access expires. */
object FamilyPhotoStore {
    fun isOwned(context: Context, value: String): Boolean {
        val uri = Uri.parse(value)
        if (uri.scheme != "file") return false
        val file = File(uri.path ?: return false).canonicalFile
        return file.parentFile == File(context.filesDir, "family_photos").canonicalFile
    }

    fun deleteOwned(context: Context, value: String) {
        if (isOwned(context, value)) File(Uri.parse(value).path!!).delete()
    }

    private fun sourceCopy(context: Context, uri: Uri): File {
        val temp = File.createTempFile("family_import_", ".img", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open this image")
            input.use { source -> temp.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= 25 * 1024 * 1024L) { "Choose an image smaller than 25 MB" }
                    output.write(buffer, 0, read)
                }
            } }
            return temp
        } catch (e: Exception) { temp.delete(); throw e }
    }

    private fun decode(file: File, maxSize: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(1.0, maxSize.toDouble() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Choose a readable photo" }
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > maxSize) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: error("Choose a readable photo")
        val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }
                8 -> setRotate(270f)
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    suspend fun read(context: Context, value: String, maxSize: Int = 512): Bitmap = withContext(Dispatchers.IO) {
        val file = sourceCopy(context, Uri.parse(value))
        try { decode(file, maxSize) } finally { file.delete() }
    }

    suspend fun importPhoto(context: Context, value: String): String = withContext(Dispatchers.IO) {
        val bitmap = read(context, value, 1024)
        val directory = File(context.filesDir, "family_photos").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "Unable to save photo" } }
            Uri.fromFile(file).toString()
        } catch (e: Exception) { file.delete(); throw e } finally { bitmap.recycle() }
    }
}

@Composable
fun FamilyPhoto(photoUri: String, emoji: String = "👤", description: String = "Family photo",
                modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember(photoUri) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, photoUri) {
        value = null
        if (photoUri.isNotBlank()) try {
            value = FamilyPhotoStore.read(context, photoUri)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = description,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else if (photoUri.isNotBlank() && !failed) CircularProgressIndicator()
        else UiText(if (photoUri.isBlank()) emoji else "Photo unavailable", fontSize = if (photoUri.isBlank()) 30.sp else 12.sp)
    }
}
