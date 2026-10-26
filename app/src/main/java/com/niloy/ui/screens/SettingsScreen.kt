package com.niloy.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niloy.domain.service.BackupValidationResult
import com.niloy.domain.service.ImportMode
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showResetDialog by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var showCustomReminderDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var showJsonImportChoiceDialog by remember { mutableStateOf(false) }
    var showJsonImportTextDialog by remember { mutableStateOf(false) }
    var csvInputText by remember { mutableStateOf("") }
    var jsonInputText by remember { mutableStateOf("") }
    var customMinutesInput by remember { mutableStateOf("${if (uiState.defaultReminderOffset !in listOf(0, 5, 10, 15, 30)) uiState.defaultReminderOffset else 20}") }
    var selectedImportMode by remember { mutableStateOf(ImportMode.MERGE) }

    // File creation launcher for JSON exports
    val createJsonBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { exportUri ->
            val content = uiState.generatedExportJson ?: return@let
            try {
                context.contentResolver.openOutputStream(exportUri, "w")?.use { outputStream ->
                    outputStream.write(content.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                Toast.makeText(context, "Backup saved successfully!", Toast.LENGTH_SHORT).show()
                viewModel.dismissExportDialog()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to write backup: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // File picker launcher for JSON import
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importUri ->
            try {
                context.contentResolver.openInputStream(importUri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val content = reader.readText()
                    viewModel.validateImportJson(content)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }


    // Toast feedback for import / export
    LaunchedEffect(uiState.importFeedbackMessage) {
        uiState.importFeedbackMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearFeedbackMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
        ) {
            // 100% Offline & Private Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "100% Offline & Private",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Your data stays on your device. No cloud sync, no tracking, no telemetry. Ever.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }


            // Focentra Integration Section
            item {
                val focentraStatus = uiState.focentraStatus

                SettingsSection(title = "INTEGRATIONS • FOCENTRA") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "Focentra Study Focus",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = when {
                                        focentraStatus == null -> "Checking installation..."
                                        !focentraStatus.isInstalled -> "Not Connected (Optional)"
                                        focentraStatus.isConnected -> "Connected • ${focentraStatus.totalImportedSessions} Sessions Synced"
                                        else -> "Not Connected (Optional)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (focentraStatus?.isConnected == true) {
                                    viewModel.syncFocentra()
                                } else {
                                    showConsentDialog = true
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (focentraStatus?.isConnected == true) "Sync" else "Connect",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Notifications & Smart Reminders Section
            item {
                SettingsSection(title = "NOTIFICATIONS & REMINDERS") {
                    SettingsRowItem(
                        icon = Icons.Outlined.Notifications,
                        title = "Routine Notifications",
                        subtitle = if (uiState.notificationsEnabled) "Smart on-device reminder alerts enabled" else "All reminder alerts muted",
                        trailing = {
                            Switch(
                                checked = uiState.notificationsEnabled,
                                onCheckedChange = { viewModel.updateNotificationsEnabled(it) }
                            )
                        }
                    )

                    if (uiState.notificationsEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Default Reminder Timing",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = when (uiState.defaultReminderOffset) {
                                                0 -> "At start time"
                                                else -> "${uiState.defaultReminderOffset} minutes before routine"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            val presetOffsets = listOf(0, 5, 10, 15, 30)
                            val isCustomSelected = uiState.defaultReminderOffset !in presetOffsets

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    0 to "At start",
                                    5 to "5m before",
                                    10 to "10m before",
                                    15 to "15m before",
                                    30 to "30m before"
                                ).forEach { (mins, label) ->
                                    FilterChip(
                                        selected = uiState.defaultReminderOffset == mins,
                                        onClick = { viewModel.updateDefaultReminderOffset(mins) },
                                        label = { Text(label, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                FilterChip(
                                    selected = isCustomSelected,
                                    onClick = { showCustomReminderDialog = true },
                                    label = {
                                        Text(
                                            text = if (isCustomSelected) "${uiState.defaultReminderOffset}m (Custom)" else "Custom...",
                                            fontSize = 12.sp
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        SettingsActionItem(
                            icon = Icons.Outlined.NotificationAdd,
                            title = "Send Test Routine Alert",
                            subtitle = "Verify on-device notification sound and banner",
                            onClick = {
                                try {
                                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                                    com.niloy.domain.service.TaskReminderScheduler.createNotificationChannel(context)
                                    val notification = androidx.core.app.NotificationCompat.Builder(context, com.niloy.domain.service.TaskReminderScheduler.CHANNEL_ID)
                                        .setSmallIcon(com.niloy.R.drawable.ic_notification)
                                        .setContentTitle("Daynexa Smart Reminder")
                                        .setContentText("Your scheduled routine reminder test is working perfectly!")
                                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                                        .setAutoCancel(true)
                                        .build()
                                    notificationManager.notify(99999, notification)
                                    Toast.makeText(context, "Test notification triggered!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Notification check: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // Data & Portability Section (JSON & CSV Export/Import)
            item {
                SettingsSection(title = "DATA & PORTABILITY") {
                    SettingsActionItem(
                        icon = Icons.Outlined.FileUpload,
                        title = "Export JSON Backup",
                        subtitle = "Full system backup (routines, history logs, settings & Focentra focus data)",
                        onClick = {
                            viewModel.generateFullBackup()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsActionItem(
                        icon = Icons.Outlined.FileDownload,
                        title = "Import JSON Backup",
                        subtitle = "Restore routines, logs and settings via file picker or clipboard JSON text",
                        onClick = {
                            showJsonImportChoiceDialog = true
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsActionItem(
                        icon = Icons.Outlined.TableChart,
                        title = "Export CSV Spreadsheet",
                        subtitle = "View and copy all routines in CSV format with a Copy button",
                        onClick = {
                            viewModel.generateCsvExport()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsActionItem(
                        icon = Icons.Outlined.TableRows,
                        title = "Import CSV File / Text",
                        subtitle = "Paste CSV text with a Paste button to bulk import routines",
                        onClick = {
                            csvInputText = ""
                            showCsvImportDialog = true
                        }
                    )
                }
            }


            // System & Release Section
            item {
                SettingsSection(title = "SYSTEM & RELEASES") {
                    SettingsActionItem(
                        icon = Icons.Outlined.SystemUpdate,
                        title = "Download Latest Version",
                        subtitle = "Check for updates and download APK on GitHub",
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Niloy-Track-Dev/Deynexa/releases"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open release link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsActionItem(
                        icon = Icons.Outlined.Code,
                        title = "Open Source Repository",
                        subtitle = "View source code, report bugs, and contribute on GitHub",
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Niloy-Track-Dev/Deynexa"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open repository link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // Danger Zone Section
            item {
                SettingsSection(title = "DANGER ZONE") {
                    SettingsActionItem(
                        icon = Icons.Outlined.DeleteForever,
                        title = "Reset All Application Data",
                        subtitle = "Erase all routines, history, and restored backups",
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { showResetDialog = true }
                    )
                }
            }

            // App Identity Info
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Daynexa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Build Better Days • v4.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Custom Reminder Timing Dialog
    if (showCustomReminderDialog) {
        AlertDialog(
            onDismissRequest = { showCustomReminderDialog = false },
            title = { Text("Custom Reminder Timing", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Set how many minutes before the routine start time you wish to receive an alert:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = customMinutesInput,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.all { it.isDigit() }) {
                                customMinutesInput = input
                            }
                        },
                        label = { Text("Minutes Before Routine") },
                        placeholder = { Text("e.g. 20, 45, 60") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val mins = customMinutesInput.toIntOrNull() ?: 0
                    if (mins > 0) {
                        val targetMins = (9 * 60 - mins + 1440) % 1440
                        val alertFormatted = com.niloy.ui.util.TimeUtils.formatMinutes(targetMins.toLong(), false)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Preview (12H format):\nRoutine at 09:00 AM → Alert at $alertFormatted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = customMinutesInput.toIntOrNull() ?: 0
                        viewModel.updateDefaultReminderOffset(mins)
                        showCustomReminderDialog = false
                    },
                    enabled = customMinutesInput.toIntOrNull()?.let { it >= 0 } == true
                ) {
                    Text("Save Offset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomReminderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Focentra Consent Dialog
    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = { Text("Connect Focentra Integration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daynexa and Focentra communicate securely via local Android App-to-App IPC with zero cloud servers or telemetry.")
                    Text("• Received data: Completed study sessions, duration, date, category, and subject.")
                    Text("• Shared data: Optional daily productivity targets and completion context.")
                    Text("You can disconnect at any time without losing your task history.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConsentDialog = false
                    viewModel.connectFocentra(true)
                }) {
                    Text("Agree & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full Backup JSON Export Dialog (File & Clipboard Options)
    if (uiState.generatedExportJson != null && uiState.exportDialogType == "FULL") {
        val exportJsonText = uiState.generatedExportJson ?: ""
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportDialog() },
            title = { Text("Full Backup Ready (v4.0.0)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your complete app state backup has been generated. Choose to save as a file or copy JSON to clipboard:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        Text(
                            text = exportJsonText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Daynexa Full Backup JSON", exportJsonText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Full Backup JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                            viewModel.dismissExportDialog()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy JSON")
                    }

                    Button(
                        onClick = {
                            createJsonBackupLauncher.launch("Daynexa_Backup_$dateStr.daynexa")
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save File")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissExportDialog() }) {
                    Text("Close")
                }
            }
        )
    }

    // JSON Import Choice Dialog (File vs Text/Clipboard)
    if (showJsonImportChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showJsonImportChoiceDialog = false },
            title = { Text("Import Backup Options", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose how you want to load your Daynexa backup file or data:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showJsonImportChoiceDialog = false
                                importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Select Backup File (.daynexa / .json)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Browse device storage for a saved backup file.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showJsonImportChoiceDialog = false
                                jsonInputText = ""
                                showJsonImportTextDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Paste / Enter JSON Text", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Paste raw backup JSON text directly from clipboard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showJsonImportChoiceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // JSON Import Text Dialog with Paste button
    if (showJsonImportTextDialog) {
        AlertDialog(
            onDismissRequest = { showJsonImportTextDialog = false },
            title = { Text("Paste Backup JSON Text", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste your backup JSON text below or use 'Paste from Clipboard':",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = jsonInputText,
                        onValueChange = { jsonInputText = it },
                        placeholder = { Text("{\"backupVersion\":5,\"appVersion\":\"4.0.0\",...}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = clipboard.primaryClip
                                    if (clipData != null && clipData.itemCount > 0) {
                                        val pastedText = clipData.getItemAt(0).text?.toString() ?: ""
                                        if (pastedText.isNotBlank()) {
                                            jsonInputText = pastedText
                                            Toast.makeText(context, "Pasted JSON from clipboard!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not read clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (jsonInputText.isNotBlank()) {
                            viewModel.validateImportJson(jsonInputText)
                            showJsonImportTextDialog = false
                        } else {
                            Toast.makeText(context, "Please paste valid JSON text", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    enabled = jsonInputText.isNotBlank()
                ) {
                    Text("Validate & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonImportTextDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export Result Dialog (JSON or CSV)
    if (uiState.generatedExportCsv != null && uiState.exportDialogType == "CSV") {
        val exportText = uiState.generatedExportCsv ?: ""
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportDialog() },
            title = { Text("CSV Routine Export Ready", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your routine CSV text is ready. Tap 'Copy' to copy to clipboard:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        Text(
                            text = exportText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Daynexa Routine CSV", exportText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "CSV copied to clipboard!", Toast.LENGTH_SHORT).show()
                        viewModel.dismissExportDialog()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy CSV")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissExportDialog() }) {
                    Text("Close")
                }
            }
        )
    }

    // CSV Import Dialog with Paste Button
    if (showCsvImportDialog) {
        AlertDialog(
            onDismissRequest = { showCsvImportDialog = false },
            title = { Text("Import Routines from CSV", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste CSV formatted routine data below or use 'Paste from Clipboard':",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = csvInputText,
                        onValueChange = { csvInputText = it },
                        placeholder = { Text("ID, Title, Description, Category, StartTime, EndTime...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = clipboard.primaryClip
                                    if (clipData != null && clipData.itemCount > 0) {
                                        val pastedText = clipData.getItemAt(0).text?.toString() ?: ""
                                        if (pastedText.isNotBlank()) {
                                            csvInputText = pastedText
                                            Toast.makeText(context, "Pasted from clipboard!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not read clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (csvInputText.isNotBlank()) {
                            viewModel.importCsvContent(csvInputText)
                            showCsvImportDialog = false
                        } else {
                            Toast.makeText(context, "Please enter or paste CSV content", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    enabled = csvInputText.isNotBlank()
                ) {
                    Text("Import CSV")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCsvImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }



    // Validation & Restore Confirmation Dialog
    uiState.validationResult?.let { validation ->
        when (validation) {
            is BackupValidationResult.Invalid -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissValidationDialog() },
                    title = { Text("Invalid Backup File", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("The selected backup could not be processed:")
                            Text(
                                text = validation.errorReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.dismissValidationDialog() }) {
                            Text("OK")
                        }
                    }
                )
            }
            is BackupValidationResult.ValidFull, is BackupValidationResult.ValidFocentra -> {
                val preview = if (validation is BackupValidationResult.ValidFull) validation.preview else (validation as BackupValidationResult.ValidFocentra).preview
                val formattedDate = try {
                    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    sdf.format(Date(preview.createdAt))
                } catch (e: Exception) {
                    "Unknown"
                }

                AlertDialog(
                    onDismissRequest = { viewModel.dismissValidationDialog() },
                    title = { Text("Backup Verification & Preview", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = preview.backupType,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Created: $formattedDate • App v${preview.appVersion}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text("Detected Items in Backup:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                if (preview.tasksCount > 0) Text("• ${preview.tasksCount} Routines / Tasks")
                                if (preview.categoriesCount > 0) Text("• ${preview.categoriesCount} Routine Categories")
                                if (preview.occurrencesCount > 0) Text("• ${preview.occurrencesCount} Routine Execution Logs")
                                if (preview.focentraSessionsCount > 0) Text("• ${preview.focentraSessionsCount} Focentra Focus Study Sessions")
                                if (preview.settingsCount > 0) Text("• ${preview.settingsCount} Preference Settings")
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            Text("Select Restoration Mode:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedImportMode == ImportMode.MERGE) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, if (selectedImportMode == ImportMode.MERGE) MaterialTheme.colorScheme.primary else Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedImportMode = ImportMode.MERGE }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedImportMode == ImportMode.MERGE,
                                        onClick = { selectedImportMode = ImportMode.MERGE }
                                    )
                                    Column {
                                        Text("Merge (Recommended)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("Preserves existing data and adds/updates records without data loss.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedImportMode == ImportMode.REPLACE) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, if (selectedImportMode == ImportMode.REPLACE) MaterialTheme.colorScheme.error else Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedImportMode = ImportMode.REPLACE }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedImportMode == ImportMode.REPLACE,
                                        onClick = { selectedImportMode = ImportMode.REPLACE },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.error)
                                    )
                                    Column {
                                        Text("Replace / Overwrite", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                        Text("Wipes local database and replaces with this backup file.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.executeImport(selectedImportMode)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = if (selectedImportMode == ImportMode.REPLACE) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                        ) {
                            Text(if (selectedImportMode == ImportMode.REPLACE) "Replace & Restore" else "Merge & Restore")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissValidationDialog() }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Data?") },
            text = { Text("This will permanently remove all tasks, history logs, custom categories, and imported Focentra session history. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllData()
                        showResetDialog = false
                        Toast.makeText(context, "All data has been reset", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsChipRow(
    options: List<Pair<String, String>>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        options.forEach { (label, value) ->
            val isSelected = selectedOption == value
            Surface(
                onClick = { onOptionSelected(value) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = 4.dp)
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
        trailing()
    }
}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = titleColor,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}
