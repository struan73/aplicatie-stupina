package ro.stupina.offline

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import ro.stupina.offline.ui.AppSection
import ro.stupina.offline.ui.StupinaViewModel
import ro.stupina.offline.ui.screens.*
import ro.stupina.offline.ui.theme.StupinaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: StupinaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StupinaTheme {
                StupinaRootApp(viewModel)
            }
        }
    }
}

@Composable
fun StupinaRootApp(vm: StupinaViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val section by vm.currentSection.collectAsState()
    val voiceOpen by vm.voiceModalOpen.collectAsState()
    val voiceText by vm.voiceInputText.collectAsState()
    val pendingVoice by vm.pendingConfirmation.collectAsState()
    val banner by vm.feedbackBanner.collectAsState()

    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.openVoiceModal()
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val data = pendingExportContent
        if (uri != null && data != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(data.toByteArray(Charsets.UTF_8))
            }
        }
        pendingExportContent = null
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (!content.isNullOrBlank()) {
                vm.restoreFromJson(content)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (granted) vm.openVoiceModal() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Dictare vocală")
            }
        },
        bottomBar = {
            NavigationBar {
                AppSection.entries.forEach { item ->
                    val icon = when (item) {
                        AppSection.STUPINA -> Icons.Default.Home
                        AppSection.STUPI -> Icons.Default.GridView
                        AppSection.OBSERVATII -> Icons.Default.EditNote
                        AppSection.CALENDAR -> Icons.Default.EventNote
                        AppSection.RAPOARTE -> Icons.Default.Assessment
                        AppSection.SETARI -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { vm.navigateTo(item) },
                        icon = { Icon(icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (banner != null) {
                Surface(color = MaterialTheme.colorScheme.secondary, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = banner!!,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            when (section) {
                AppSection.STUPINA -> StupinaHomeScreen(
                    vm = vm,
                    onAddGeneralObs = { vm.navigateTo(AppSection.OBSERVATII) },
                    onAddIndividualObs = { vm.navigateTo(AppSection.OBSERVATII) }
                )
                AppSection.STUPI -> HivesScreen(vm)
                AppSection.OBSERVATII -> ObservationsScreen(vm)
                AppSection.CALENDAR -> CalendarScreen(vm)
                AppSection.RAPOARTE -> ReportsScreen(vm) { filename, content ->
                    pendingExportContent = content
                    createDocumentLauncher.launch(filename)
                }
                AppSection.SETARI -> SettingsBackupScreen(
                    vm = vm,
                    onExportJson = { filename ->
                        scope.launch {
                            pendingExportContent = vm.buildBackupJson()
                            createDocumentLauncher.launch(filename)
                        }
                    },
                    onRestoreJson = {
                        openBackupLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                )
            }
        }

        if (voiceOpen) {
            VoiceDictationSheet(vm = vm, pending = pendingVoice, inputText = voiceText)
        }
    }
}
