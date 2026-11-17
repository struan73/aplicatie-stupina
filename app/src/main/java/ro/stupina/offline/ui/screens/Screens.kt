package ro.stupina.offline.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ro.stupina.offline.data.db.*
import ro.stupina.offline.ui.AppSection
import ro.stupina.offline.ui.PendingVoiceConfirmation
import ro.stupina.offline.ui.StupinaViewModel
import ro.stupina.offline.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("ro", "RO"))

@Composable
fun StupinaHomeScreen(vm: StupinaViewModel, onAddGeneralObs: () -> Unit, onAddIndividualObs: () -> Unit) {
    val hives by vm.allHives.collectAsState()
    val observations by vm.allObservations.collectAsState()
    val active = hives.filter { !it.isArchived }
    val productive = active.count { it.status == "Productivă" }
    val nuclei = active.count { it.familyType == "Nucleu" }
    val weak = active.count { it.status == "Slabă" }
    val queenless = active.count { it.status == "Fără matcă" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Stupina mea (Offline)", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.openVoiceModal() },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HoneyAmber, contentColor = CharcoalInk)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("COMANDĂ VOCALĂ / DICTARE", fontWeight = FontWeight.Bold)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Total familii", active.size.toString(), Modifier.weight(1f))
                KpiCard("Productive", productive.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Nuclee", nuclei.toString(), Modifier.weight(1f))
                KpiCard("Slabe", weak.toString(), Modifier.weight(1f))
                KpiCard("Fără matcă", queenless.toString(), Modifier.weight(1f), highlightError = queenless > 0)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onAddGeneralObs,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+ Obs. generală")
                }
                Button(
                    onClick = onAddIndividualObs,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CharcoalInk, contentColor = SunlitCanvas)
                ) {
                    Text("+ Obs. stup")
                }
            }
        }

        item {
            Text("Ultimele observații", style = MaterialTheme.typography.titleLarge)
        }

        items(observations.take(6)) { obs ->
            ObservationCard(obs)
        }
    }
}

@Composable
fun HivesScreen(vm: StupinaViewModel) {
    val hives by vm.allHives.collectAsState()
    val queens by vm.allQueens.collectAsState()
    val observations by vm.allObservations.collectAsState()
    val selectedHiveId by vm.selectedHiveId.collectAsState()
    val filter by vm.hiveFilter.collectAsState()
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val selectedHive = hives.firstOrNull { it.id == selectedHiveId }
    if (selectedHive != null) {
        HiveDetailScreen(
            hive = selectedHive,
            queens = queens.filter { it.hiveId == selectedHive.id },
            observations = observations.filter { it.hiveId == selectedHive.id },
            onBack = { vm.navigateTo(AppSection.STUPI) },
            onArchive = { vm.archiveHive(selectedHive) },
            onChangeQueen = { year, origin, notes -> vm.changeQueen(selectedHive, year, origin, notes) }
        )
        return
    }

    val filtered = hives.filter { hive ->
        val matchesQuery = query.isBlank() || hive.hiveNumber.toString().contains(query) || hive.locationZone.contains(query, ignoreCase = true)
        val matchesFilter = when (filter) {
            "Arhivate" -> hive.isArchived
            "Toate" -> !hive.isArchived
            "Nucleu" -> !hive.isArchived && hive.familyType == "Nucleu"
            "Slabă" -> !hive.isArchived && hive.status == "Slabă"
            "Fără matcă" -> !hive.isArchived && hive.status == "Fără matcă"
            "Productivă" -> !hive.isArchived && hive.status == "Productivă"
            else -> !hive.isArchived
        }
        matchesQuery && matchesFilter
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Stupi (${filtered.size})", style = MaterialTheme.typography.headlineMedium)
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CharcoalInk, contentColor = SunlitCanvas)
            ) {
                Text("+ Stup nou")
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Caută număr stup sau zonă") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Toate", "Productivă", "Nucleu", "Slabă", "Fără matcă", "Arhivate").forEach { tag ->
                FilterChip(
                    selected = filter == tag,
                    onClick = { vm.setHiveFilter(tag) },
                    label = { Text(tag) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered) { hive ->
                Surface(
                    onClick = { vm.openHiveDetail(hive.id) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StoneBorder),
                    color = WarmSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Stupul #${hive.hiveNumber} · ${hive.familyType}", style = MaterialTheme.typography.titleMedium)
                            Text("${hive.hiveType} · ${hive.broodFrames}/${hive.totalFrames} rame puiet · ${hive.locationZone}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            hive.status,
                            fontWeight = FontWeight.Bold,
                            color = if (hive.status == "Fără matcă") AlertRed else CharcoalInk
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var num by remember { mutableStateOf("") }
        var zone by remember { mutableStateOf("Rândul 1") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Adaugă stup nou") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = num, onValueChange = { num = it }, label = { Text("Număr unic stup") })
                    OutlinedTextField(value = zone, onValueChange = { zone = it }, label = { Text("Zonă vatră") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = num.toIntOrNull()
                    if (parsed != null) {
                        vm.addHive(parsed, "Producție", "Dadant 10 rame", "Productivă", 5, 10, zone, 2026, "Proprie")
                        showAddDialog = false
                    }
                }) { Text("Salvează") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Anulează") } }
        )
    }
}

@Composable
fun HiveDetailScreen(
    hive: HiveEntity,
    queens: List<QueenEntity>,
    observations: List<ObservationEntity>,
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onChangeQueen: (Int, String, String) -> Unit
) {
    var showQueenDialog by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Înapoi la stupi")
            }
            Spacer(Modifier.height(10.dp))
            Text("Fișa Stupului #${hive.hiveNumber}", style = MaterialTheme.typography.headlineMedium)
            Text("${hive.familyType} · ${hive.hiveType} · Stare: ${hive.status}", style = MaterialTheme.typography.bodyLarge)
            Text("Puiet: ${hive.broodFrames} rame · Total: ${hive.totalFrames} rame · Zonă: ${hive.locationZone}")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { showQueenDialog = true }, modifier = Modifier.weight(1f).height(50.dp)) {
                    Text("Schimbă matca")
                }
                if (!hive.isArchived) {
                    OutlinedButton(onClick = onArchive, modifier = Modifier.weight(1f).height(50.dp)) {
                        Text("Arhivează")
                    }
                }
            }
        }

        item {
            Text("Regine (${queens.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(queens) { q ->
            Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, StoneBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("An ${q.hatchYear} · ${q.origin} · ${if (q.isActive) "Activă" else "Istoric"}", fontWeight = FontWeight.SemiBold)
                    if (q.notes.isNotBlank()) Text(q.notes)
                }
            }
        }

        item {
            Text("Istoric observații (${observations.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(observations) { obs -> ObservationCard(obs) }
    }

    if (showQueenDialog) {
        var year by remember { mutableStateOf("2026") }
        var origin by remember { mutableStateOf("Proprie") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQueenDialog = false },
            title = { Text("Schimbare matcă stup #${hive.hiveNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("An eclozionare") })
                    OutlinedTextField(value = origin, onValueChange = { origin = it }, label = { Text("Origine") })
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Observații") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onChangeQueen(year.toIntOrNull() ?: 2026, origin, notes)
                    showQueenDialog = false
                }) { Text("Salvează") }
            },
            dismissButton = { TextButton(onClick = { showQueenDialog = false }) { Text("Anulează") } }
        )
    }
}

@Composable
fun ObservationsScreen(vm: StupinaViewModel) {
    val observations by vm.allObservations.collectAsState()
    var text by remember { mutableStateOf("") }
    var hiveNum by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Observație generală") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Evidența Observațiilor", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = hiveNum, onValueChange = { hiveNum = it }, label = { Text("Nr. stup (gol = toată stupina)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Categorie") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text observație") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        vm.addManualObservation(hiveNum.toIntOrNull(), category, text, null, null)
                        text = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Salvează observația")
            }
        }
        items(observations) { obs -> ObservationCard(obs) }
    }
}

@Composable
fun CalendarScreen(vm: StupinaViewModel) {
    val reminders by vm.allReminders.collectAsState()
    var title by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("7") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Calendar și Memento-uri", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Sarcină (ex: Verificare botci)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Peste câte zile") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        vm.addReminder(null, title, "Verificare periodică", days.toIntOrNull() ?: 7)
                        title = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Adaugă memento") }
        }
        items(reminders) { rem ->
            Surface(
                onClick = { vm.toggleReminder(rem) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, StoneBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rem.isCompleted, onCheckedChange = { vm.toggleReminder(rem) })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(rem.title, fontWeight = FontWeight.SemiBold)
                        Text("${rem.category} · Scadență: ${dateFmt.format(Date(rem.dueDate))}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsScreen(vm: StupinaViewModel, onShareText: (String, String) -> Unit) {
    val hives by vm.allHives.collectAsState()
    val observations by vm.allObservations.collectAsState()
    val queens by vm.allQueens.collectAsState()
    val totalHoney = observations.mapNotNull { it.honeyKg }.sum()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Rapoarte și Export", style = MaterialTheme.typography.headlineMedium)
        KpiCard("Familii active / Total", "${hives.count { !it.isArchived }} / ${hives.size}", Modifier.fillMaxWidth())
        KpiCard("Regine înregistrate", queens.size.toString(), Modifier.fillMaxWidth())
        KpiCard("Miere înregistrată (kg)", String.format(Locale("ro", "RO"), "%.1f kg", totalHoney), Modifier.fillMaxWidth())
        Button(
            onClick = { onShareText("stupina_export.csv", vm.buildCsvExport()) },
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text("Exportă CSV (compatibil Excel)") }
        Button(
            onClick = { onShareText("stupina_raport.xls", vm.buildExcelExport()) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CharcoalInk, contentColor = SunlitCanvas)
        ) { Text("Exportă Excel (.xls)") }
    }
}

@Composable
fun SettingsBackupScreen(vm: StupinaViewModel, onExportJson: (String) -> Unit, onRestoreJson: () -> Unit) {
    val speechStatus = remember { vm.speechManager.checkAvailability() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Setări și Backup Offline", style = MaterialTheme.typography.headlineMedium)
        Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, StoneBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Motor recunoaștere vocală offline", fontWeight = FontWeight.Bold)
                Text(speechStatus.description)
            }
        }
        Button(onClick = { onExportJson("stupina_backup.json") }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Crează Backup Manual (JSON)")
        }
        OutlinedButton(onClick = onRestoreJson, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Restaurează Backup din fișier")
        }
    }
}

@Composable
fun VoiceDictationSheet(
    vm: StupinaViewModel,
    pending: PendingVoiceConfirmation?,
    inputText: String
) {
    val isListening by vm.speechManager.isListening.collectAsState()
    val errorMsg by vm.speechManager.errorMessage.collectAsState()

    AlertDialog(
        onDismissRequest = { vm.closeVoiceModal() },
        title = { Text("Dictare & Comenzi Vocale (Offline)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.startOfflineVoiceListening() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isListening) AlertRed else HoneyAmber, contentColor = if (isListening) Color.White else CharcoalInk)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isListening) "Ascult..." else "Pornește microfonul offline")
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, color = AlertRed, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { vm.updateVoiceInputText(it) },
                    label = { Text("Transcript / Alternativă locală") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                if (pending != null) {
                    Text(pending.parsed.uncertaintyReason ?: "Confirmați asocierea:", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.confirmAndSavePendingVoice(forceGeneral = true) }) { Text("Generală stupină") }
                        if (pending.selectedHiveNumber != null) {
                            Button(onClick = { vm.confirmAndSavePendingVoice(overrideHiveNumber = pending.selectedHiveNumber, forceGeneral = false) }) {
                                Text("Stup #${pending.selectedHiveNumber}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.processVoiceText(inputText) }) { Text("Interpretează") }
        },
        dismissButton = {
            TextButton(onClick = { vm.closeVoiceModal() }) { Text("Închide") }
        }
    )
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier, highlightError: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (highlightError) AlertRed else StoneBorder),
        color = WarmSurface,
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = if (highlightError) AlertRed else CharcoalInk)
        }
    }
}

@Composable
private fun ObservationCard(obs: ObservationEntity) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StoneBorder),
        color = WarmSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val target = obs.hiveNumberSnapshot?.let { "Stup #$it" } ?: "Întreaga stupină"
            Text("$target · ${obs.category} · ${dateFmt.format(Date(obs.timestamp))}", style = MaterialTheme.typography.bodyMedium)
            Text(obs.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
