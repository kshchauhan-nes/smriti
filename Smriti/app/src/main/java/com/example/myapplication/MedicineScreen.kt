package com.example.myapplication

import com.example.myapplication.ui.UiText

import com.example.myapplication.speech.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.MedicineItem
import com.example.myapplication.data.SmritiRepository
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.reminders.ReminderSettingsCard
import com.example.myapplication.reminders.ReminderScheduler
import com.example.myapplication.reminders.ScheduleTime
import kotlinx.coroutines.launch
import com.example.myapplication.reminders.DailyRefreshEffect

@Composable
fun MedicineScreen(
    repository: SmritiRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var medicines by remember { mutableStateOf<List<MedicineItem>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refresh() {
        coroutineScope.launch {
            medicines = repository.getAllMedicines()
        }
    }

    DailyRefreshEffect {
        refresh()
    }

    NarrationScreen("Medicine tracker. " +
        if (medicines.isEmpty()) "No medicines added yet. Use the plus button to add the prescribed schedule."
        else medicines.joinToString("\n") { "${it.name}. ${it.dosage}. Scheduled at ${it.scheduledTime}. " +
            (if (it.isTaken) "Marked taken today. " else "Not marked taken today. ") + it.instructions } +
            "\nOnly mark a dose taken after confirming it was taken. An unchecked entry does not prove a dose was missed. " +
            "Reminders are scheduled fifteen minutes before, at the scheduled time, and thirty minutes later if still unchecked.", assamese = "ঔষধৰ তালিকা। " + (if (medicines.isEmpty()) "এতিয়াও কোনো ঔষধ যোগ কৰা হোৱা নাই।" else medicines.joinToString("\n") { "${it.name}। ${it.dosage}। সময় ${it.scheduledTime}। " + (if (it.isTaken) "আজি খোৱা বুলি চিহ্নিত কৰা হৈছে। " else "আজি খোৱা বুলি চিহ্নিত কৰা হোৱা নাই। ") + it.instructions }) + " ঔষধ খোৱাৰ পিছতহে চিহ্নিত কৰিব। চিহ্ন নথকাৰ অৰ্থ ঔষধ খোৱা নাই বুলি নিশ্চিত নহয়। নিৰ্ধাৰিত সময়ৰ পোন্ধৰ মিনিট আগতে, সময়ত আৰু চিহ্নিত নকৰিলে ত্ৰিশ মিনিট পিছত সোঁৱৰাই দিয়া হ’ব।")
    val takenCount = medicines.count { it.isTaken }
    val totalCount = medicines.size

    val background = Brush.verticalGradient(
        listOf(
            Color(0xFFFFF4F4),
            Color(0xFFFFFEFA),
            Color(0xFFE8F5E9)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onBack) {
            UiText("← Back", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                UiText(
                    text = "Medicine Tracker 💊",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF233449)
                )
                UiText(
                    text = "$takenCount of $totalCount taken today",
                    fontSize = 16.sp,
                    color = Color(0xFF66758A)
                )
            }

            Surface(
                onClick = { showAddDialog = true },
                shape = CircleShape,
                color = Color(0xFFE57373),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    UiText("+", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        ReminderSettingsCard()

        // Adherence Progress Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (takenCount == totalCount && totalCount > 0) Color(0xFF6952C5) else Color(0xFFD32F2F)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UiText(
                    text = if (takenCount == totalCount && totalCount > 0) "🎉" else "⏰",
                    fontSize = 36.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    UiText(
                        text = if (takenCount == totalCount && totalCount > 0)
                            "All Medicines Done!"
                        else
                            "${totalCount - takenCount} Medicines Remaining",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    UiText(
                        text = "100% Offline tracking saved locally",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        UiText(
            text = "Today's Schedule",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449)
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (medicines.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                    UiText("No medicines added yet.", color = Color(0xFF78909C))
                }
            }
        } else {
            medicines.forEach { med ->
                MedicineCard(
                    medicine = med,
                    onToggle = { isChecked ->
                        coroutineScope.launch {
                            repository.toggleMedicineTaken(med.id, isChecked)
                            ReminderScheduler.reconcile(context)
                            refresh()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    if (showAddDialog) {
        AddMedicineDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, dosage, time, instructions ->
                coroutineScope.launch {
                    repository.addMedicine(
                        MedicineItem(
                            name = name,
                            dosage = dosage,
                            scheduledTime = time,
                            instructions = instructions,
                            isTaken = false
                        )
                    )
                    ReminderScheduler.reconcile(context)
                    refresh()
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun MedicineCard(
    medicine: MedicineItem,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (medicine.isTaken) Color(0xFFE8F5E9) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (medicine.isTaken) 1.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (medicine.isTaken) Color(0xFFC8E6C9) else Color(0xFFFFEBEE)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    UiText(text = if (medicine.isTaken) "✅" else "💊", fontSize = 26.sp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicine.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (medicine.isTaken) Color(0xFF2E7D32) else Color(0xFF233449)
                )
                UiText(
                    text = "${medicine.scheduledTime} • ${medicine.dosage}",
                    fontSize = 14.sp,
                    color = Color(0xFF66758A)
                )
                if (medicine.instructions.isNotEmpty()) {
                    Text(
                        text = medicine.instructions,
                        fontSize = 12.sp,
                        color = Color(0xFF90A4AE)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Checkbox(
                checked = medicine.isTaken,
                onCheckedChange = { onToggle(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF2E7D32)
                )
            )
        }
    }
}

@Composable
fun AddMedicineDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, dosage: String, time: String, instructions: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("08:00 AM") }
    var instructions by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            NarratedTitle("Add New Medicine", "Add a medicine. Enter its name, prescribed dosage, time and any instructions. Use a valid time such as 09:00 AM. Save to add the medicine, or Cancel to return.")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { UiText("Medicine Name (e.g. Donepezil)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { UiText("Dosage (e.g. 1 tablet)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { UiText("Time (e.g. 09:00 AM or 21:00)") },
                    isError = ScheduleTime.parse(time) == null,
                    supportingText = { if (ScheduleTime.parse(time) == null) UiText("Enter a valid time, such as 09:00 AM or 21:00") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { UiText("Notes (e.g. after breakfast)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && ScheduleTime.parse(time) != null) {
                        onAdd(name.trim(), dosage.trim(), time.trim(), instructions.trim())
                    }
                },
                enabled = name.isNotBlank() && ScheduleTime.parse(time) != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6952C5))
            ) {
                UiText("Save Locally")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                UiText("Cancel")
            }
        }
    )
}
