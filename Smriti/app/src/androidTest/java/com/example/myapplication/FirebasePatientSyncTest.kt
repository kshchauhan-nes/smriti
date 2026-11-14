package com.example.myapplication

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.cloud.*
import com.example.myapplication.data.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Runs against the configured project; creates and removes only its own disposable accounts/data. */
class FirebasePatientSyncTest {
    @Test fun offlineWritesRestoreOnAnotherClientAndOtherPatientsAreDenied() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CloudSession.setLocal(context, false)
        CloudSession.initialize(context)
        check(CloudSession.auth.currentUser == null) { "Sign out before running this test; existing patient sessions must be preserved." }
        val suffix = UUID.randomUUID().toString()
        val email = "smriti-test-$suffix@example.com"
        val password = UUID.randomUUID().toString() + "A9!"
        val secondApp = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "test-$suffix")
        val otherAuth = FirebaseAuth.getInstance(secondApp)
        val otherDb = FirebaseFirestore.getInstance(secondApp)
        val photo = File(context.cacheDir, "firebase-test-$suffix.jpg")
        var owner: String? = null
        var outsider: String? = null
        try {
            withTimeout(90_000) {
                CloudSession.register(email, password, "Sync test patient")
                owner = CloudSession.auth.currentUser!!.uid
                while (!CloudSession.ready.value) { CloudSession.error.value?.let { error(it) }; delay(100) }
                val repository = SmritiRepository(context)
                val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.BLUE)
                photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }; bitmap.recycle()
                CloudSession.db.disableNetwork().result()
                val med = repository.addMedicine(MedicineItem(name = "Sync fixture", dosage = "Test only", scheduledTime = "08:00 AM"))
                repository.toggleMedicineTaken(med, true)
                repository.savePatientProfile(PatientProfile("Offline profile", "Test caregiver", "", "", "English"))
                val family = repository.saveFamilyProfile(FamilyMember(name = "Fixture", relation = "Relative", photoUri = Uri.fromFile(photo).toString(), notes = "Test photo"))
                assertTrue(repository.getAllMedicines().single().isTaken)
                assertEquals("Offline profile", repository.getPatientProfile().patientName)
                val pending = CloudSession.db.collection("patients").document(owner!!).collection("medicines").document("$med").get(Source.CACHE).result()
                assertTrue(pending.metadata.hasPendingWrites())
                CloudSession.db.enableNetwork().result()
                CloudSession.syncNow()
                otherAuth.signInWithEmailAndPassword(email, password).result()
                val root = otherDb.collection("patients").document(owner!!)
                assertEquals("Offline profile", root.get(Source.SERVER).result().getString("patientName"))
                assertEquals("Sync fixture", root.collection("medicines").document("$med").get(Source.SERVER).result().getString("name"))
                assertTrue(root.collection("photos").document("$family").get(Source.SERVER).result().getBlob("image")!!.toBytes().isNotEmpty())
                otherAuth.signOut()
                outsider = otherAuth.createUserWithEmailAndPassword("other-$suffix@example.com", password).result().user!!.uid
                val denied = runCatching { root.get(Source.SERVER).result() }.exceptionOrNull()
                assertTrue("Another patient must not read the owner", denied is FirebaseFirestoreException && denied.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                val deniedWrite = runCatching { root.set(mapOf("patientName" to "Unauthorized")).result() }.exceptionOrNull()
                assertTrue("Another patient must not write the owner", deniedWrite is FirebaseFirestoreException && deniedWrite.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                assertFalse(otherDb.collection("patients").document(outsider!!).get(Source.SERVER).result().exists())
                repository.close()
            }
        } finally {
            withContext(NonCancellable) {
                CloudSession.db.enableNetwork().result()
                owner?.let { uid ->
                    val root = CloudSession.db.collection("patients").document(uid)
                    for (name in CloudSession.collections) {
                        root.collection(name).get(Source.SERVER).result().documents.forEach { it.reference.delete().result() }
                    }
                    root.delete().result()
                    CloudSession.auth.currentUser?.delete()?.result()
                }
                if (outsider != null) otherAuth.currentUser?.delete()?.result()
                otherAuth.signOut(); otherDb.terminate().result(); otherDb.clearPersistence().result(); secondApp.delete()
                photo.delete()
            }
        }
    }
}
