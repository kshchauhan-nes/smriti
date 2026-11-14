package com.example.myapplication

import com.example.myapplication.ui.UiText

import com.example.myapplication.speech.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.FamilyMember
import com.example.myapplication.data.SmritiRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FamilyProfileDialog(member: FamilyMember?, repository: SmritiRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(member?.name ?: "") }
    var relation by rememberSaveable { mutableStateOf(member?.relation ?: "") }
    var notes by rememberSaveable { mutableStateOf(member?.notes ?: "") }
    var photo by rememberSaveable { mutableStateOf(member?.photoUri ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { photo = uri.toString(); error = null }
    }
    val valid = photo.isNotBlank() && name.isNotBlank() && relation.isNotBlank() && notes.isNotBlank()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { NarratedTitle(if (member == null) "Add family photo" else "Edit family photo & details", "Choose a clear photograph of one person. Enter their name, relationship, and information about the person or photograph. All fields are required. Save photo and details when finished, or Cancel to return.") },
        text = {
            Column(modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UiText("Choose a clear photo of one person. All fields are required.")
                if (photo.isNotBlank()) FamilyPhoto(photo, description = "Selected family photo",
                    modifier = Modifier.fillMaxWidth().height(150.dp))
                OutlinedButton(enabled = !saving, onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.fillMaxWidth()) { UiText(if (photo.isBlank()) "Choose photo *" else "Change photo") }
                OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !saving,
                    label = { UiText("Person's name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = relation, onValueChange = { relation = it }, enabled = !saving,
                    label = { UiText("Relationship *") }, placeholder = { UiText("e.g. Daughter") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, enabled = !saving,
                    label = { UiText("About this person / photo *") },
                    placeholder = { UiText("A familiar memory, where the photo was taken, or something they enjoy") },
                    minLines = 3, modifier = Modifier.fillMaxWidth())
                UiText("These details will appear after the recall answer. The photo is saved offline.",
                    style = MaterialTheme.typography.bodySmall)
                error?.let { UiText(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = valid && !saving, onClick = {
                saving = true
                error = null
                scope.launch {
                    try {
                        // Finish the photo + database save together if the activity is recreated.
                        withContext(NonCancellable) {
                            repository.saveFamilyProfile(FamilyMember(id = member?.id ?: 0L, name = name,
                                relation = relation, notes = notes, photoUri = photo,
                                avatarEmoji = member?.avatarEmoji ?: "👤"))
                        }
                        onSaved()
                    } catch (e: Exception) {
                        error = e.message ?: "Unable to save. Please choose the photo again."
                    } finally { saving = false }
                }
            }) { UiText(if (saving) "Saving…" else "Save photo & details") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { UiText("Cancel") } }
    )
}
