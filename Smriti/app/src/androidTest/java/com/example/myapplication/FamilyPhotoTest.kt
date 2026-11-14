package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.FamilyMember
import com.example.myapplication.data.SmritiRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FamilyPhotoTest {
    @Test fun photoAndRequiredDetailsPersistAndCanBeEditedAndDeleted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("photo_test", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val repository = SmritiRepository(context)
        var id = 0L
        try {
            id = repository.saveFamilyProfile(FamilyMember(name = "Photo test", relation = "Relative",
                notes = "Information about this image", photoUri = Uri.fromFile(source).toString()))
            val saved = repository.getAllFamilyMembers().first { it.id == id }
            assertTrue(FamilyPhotoStore.isOwned(context, saved.photoUri))
            assertNotEquals(Uri.fromFile(source).toString(), saved.photoUri)
            source.delete()
            val decoded = FamilyPhotoStore.read(context, saved.photoUri, 1024)
            assertEquals(1024, decoded.width)
            assertEquals(512, decoded.height)
            decoded.recycle()
            repository.saveFamilyProfile(saved.copy(notes = "Updated information"))
            val edited = repository.getAllFamilyMembers().first { it.id == id }
            assertEquals("Updated information", edited.notes)
            assertEquals(saved.photoUri, edited.photoUri)
            repository.deleteFamilyMember(id)
            assertFalse(File(Uri.parse(saved.photoUri).path!!).exists())
            assertFalse(repository.getAllFamilyMembers().any { it.id == id })
            id = 0L
        } finally {
            if (id != 0L) repository.deleteFamilyMember(id)
            source.delete()
            repository.close()
        }
    }

    @Test fun missingImageInformationIsRejectedBeforeSaving() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SmritiRepository(context)
        try {
            val before = repository.getAllFamilyMembers().size
            val result = runCatching { repository.saveFamilyProfile(FamilyMember(name = "Invalid", relation = "Relative",
                photoUri = "content://not-used", notes = " ")) }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, repository.getAllFamilyMembers().size)
        } finally { repository.close() }
    }
}
