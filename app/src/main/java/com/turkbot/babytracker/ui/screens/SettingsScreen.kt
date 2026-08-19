/**
 * Baby Tracker — Native Android (Kotlin)
 *
 * A privacy-first baby tracking app with Nostr-based encrypted storage
 * and parent-to-parent messaging.
 *
 * Copyright (c) 2026 Turkey
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for full license details.
 */

package com.turkbot.babytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.Child
import com.turkbot.babytracker.nostr.NostrManager
import com.turkbot.babytracker.nostr.crypto.SignerType
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: BabyViewModel, nostrManager: NostrManager) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val children by viewModel.children.collectAsState()
    val signer by nostrManager.signer.collectAsState()
    val relayConnected by nostrManager.relayConnected.collectAsState()
    val currentRelays by nostrManager.currentRelays.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddChild by remember { mutableStateOf(false) }
    var showImportKey by remember { mutableStateOf(false) }
    var nsecInput by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var amberError by rememberSaveable { mutableStateOf<String?>(null) }
    var partnerNpubInput by rememberSaveable { mutableStateOf("") }
    var partnerError by rememberSaveable { mutableStateOf<String?>(null) }

    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val amberInstalled = remember { viewModel.isAmberInstalled() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ─── Measurement Units ───────────────────────────
        item {
            SectionHeader("Measurement Units")
        }

        item {
            var unitSystem by remember { mutableStateOf("metric") }
            val context = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(Unit) {
                unitSystem = context.getSharedPreferences("baby_tracker_prefs", Context.MODE_PRIVATE)
                    .getString("unit_system", "metric") ?: "metric"
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Display Units",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Sets the default for feeding, pumping, weight, and height across all screens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    val systems = listOf("metric" to "Metric (ml, kg, cm)", "imperial" to "Imperial (fl oz, lb, in)")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        systems.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = unitSystem == value,
                                onClick = {
                                    unitSystem = value
                                    context.getSharedPreferences("baby_tracker_prefs", Context.MODE_PRIVATE)
                                        .edit().putString("unit_system", value).apply()
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, systems.size)
                            ) { Text(if (value == "metric") "Metric" else "Imperial") }
                        }
                    }
                }
            }
        }

        // ─── Nostr Identity ─────────────────────────────
        item {
            SectionHeader("Nostr Identity")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (signer != null) {
                        Text(
                            "Your npub",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(signer!!.npub, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (signer!!.type == SignerType.AMBER) "Amber (NIP-55)" else "Local key"
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Share this npub with the other parent so they can send you messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.clearNostrIdentity() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Log Out / Reset")
                        }
                        // ── Offer Amber switch if currently using a local key ──
                        if (signer!!.type != SignerType.AMBER) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                "Switch to Amber signer (NIP-55):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    amberError = null
                                    if (amberInstalled) {
                                        scope.launch {
                                            val result = nostrManager.loginWithAmber()
                                            if (result == null) {
                                                amberError = "Amber login failed or was denied"
                                            }
                                        }
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.greenart7c3.amber/"))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            amberError = "Install Amber from F-Droid: f-droid.org/packages/com.greenart7c3.amber"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (amberInstalled) "Log in with Amber" else "Log in with Amber (install)")
                            }
                            if (!amberInstalled) {
                                Text(
                                    "Amber is a Nostr signer app — your private key stays in Amber, this app never sees it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (amberError != null) {
                                Text(
                                    amberError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        Text(
                            "No Nostr identity yet.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.generateNostrIdentity() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Generate New Key")
                            }
                            OutlinedButton(
                                onClick = { showImportKey = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Import nsec")
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Button(
                                onClick = {
                                    amberError = null
                                    if (amberInstalled) {
                                        scope.launch {
                                            val result = nostrManager.loginWithAmber()
                                            if (result == null) {
                                                amberError = "Amber login failed or was denied"
                                            }
                                        }
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.greenart7c3.amber/"))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            amberError = "Install Amber from F-Droid: f-droid.org/packages/com.greenart7c3.amber"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (amberInstalled) "Log in with Amber" else "Log in with Amber (install)")
                            }
                            if (!amberInstalled) {
                                Text(
                                    "Amber is a Nostr signer app — your private key stays in Amber, this app never sees it. Tap above to install from F-Droid.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (amberError != null) {
                                Text(
                                    amberError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Partner Sync ─────────────────────────────────
        item {
            SectionHeader("Partner Sync")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (signer != null) {
                        Text(
                            "Link with the other parent to share baby data automatically. " +
                                "When either of you adds a feeding, sleep, or weight, the other " +
                                "phone receives it on next sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        val currentPartner by viewModel.partnerNpub.collectAsState()
                        if (currentPartner != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    currentPartner!!.take(24) + "...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.setPartnerNpub(null)
                                    partnerNpubInput = ""
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Unlink Partner")
                            }
                        } else {
                            OutlinedTextField(
                                value = partnerNpubInput,
                                onValueChange = {
                                    partnerNpubInput = it
                                    partnerError = null
                                },
                                label = { Text("Partner's npub") },
                                placeholder = { Text("npub1...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (partnerError != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    partnerError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val input = partnerNpubInput.trim()
                                    if (!input.startsWith("npub1")) {
                                        partnerError = "Enter a valid npub (starts with npub1...)"
                                    } else {
                                        viewModel.setPartnerNpub(input)
                                        partnerNpubInput = ""
                                        // Trigger a backup so the partner gets our data immediately
                                        viewModel.exportBackup()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Link Partner")
                            }
                        }
                    } else {
                        Text(
                            "Sign in with a Nostr identity first to enable partner sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ─── Relay Status ────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Relay Status", style = MaterialTheme.typography.bodyLarge)
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(if (relayConnected) "Connected" else "Disconnected")
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (relayConnected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer,
                            labelColor = if (relayConnected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            }
        }

        item {
            // ─── Active Relay List ────────────────────────────
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                currentRelays.forEach { url ->
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
                    )
                }
            }
        }

        // ─── Reminders ───────────────────────────────────
        item {
            SectionHeader("Reminders")
        }

        item {
            var reminderInterval by remember { mutableStateOf(0) }
            val context = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(Unit) {
                reminderInterval = context.getSharedPreferences("baby_tracker_prefs", Context.MODE_PRIVATE)
                    .getInt("reminder_interval", 0)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Feeding Reminder",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Get a notification at regular intervals to remind you to feed baby.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    val intervals = listOf(0 to "Off", 90 to "Every 1.5h", 120 to "Every 2h", 150 to "Every 2.5h", 180 to "Every 3h")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        intervals.forEachIndexed { index, (mins, label) ->
                            SegmentedButton(
                                selected = reminderInterval == mins,
                                onClick = {
                                    reminderInterval = mins
                                    viewModel.setReminderInterval(mins)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, intervals.size)
                            ) { Text(label) }
                        }
                    }
                }
            }
        }

        // ─── App Update ───────────────────────────────────
        item {
            SectionHeader("App Updates")
        }

        item {
            val updateInfo by viewModel.updateInfo.collectAsState()
            val updateChecking by viewModel.updateChecking.collectAsState()
            val updateDownloading by viewModel.updateDownloading.collectAsState()
            val updateMessage by viewModel.updateMessage.collectAsState()
            var autoUpdate by remember { mutableStateOf(viewModel.isAutoUpdateEnabled()) }
            var showUpdateDialog by remember { mutableStateOf(false) }

            // Auto-check on screen entry if enabled
            LaunchedEffect(Unit) {
                if (viewModel.isAutoUpdateEnabled()) {
                    viewModel.checkForUpdate()
                }
            }

            // Show dialog when update found and auto-update is on
            LaunchedEffect(updateInfo) {
                if (updateInfo != null && autoUpdate) {
                    showUpdateDialog = true
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Auto-Update",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Check Forgejo for new versions and install them automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Check automatically on launch")
                        Switch(
                            checked = autoUpdate,
                            onCheckedChange = {
                                autoUpdate = it
                                viewModel.setAutoUpdateEnabled(it)
                                if (it) viewModel.checkForUpdate()
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Current version",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "v${viewModel.currentVersionName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (updateInfo != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Latest version",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "v${updateInfo!!.versionName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (updateChecking) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Checking for updates...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (updateInfo != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Update v${updateInfo!!.versionName} available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (updateMessage != null) {
                        Text(
                            updateMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updateMessage!!.startsWith("You're up to date"))
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            "Last checked: tap \"Check Now\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.checkForUpdate() },
                            enabled = !updateChecking
                        ) { Text("Check Now") }
                        if (updateInfo != null) {
                            Button(
                                onClick = { showUpdateDialog = true },
                                enabled = !updateDownloading
                            ) {
                                if (updateDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Download & Install")
                                }
                            }
                        }
                    }
                }
            }

            // Update confirmation dialog
            if (showUpdateDialog && updateInfo != null) {
                AlertDialog(
                    onDismissRequest = { showUpdateDialog = false },
                    title = { Text("Update to v${updateInfo!!.versionName}") },
                    text = {
                        Column {
                            Text(updateInfo!!.releaseName)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                updateInfo!!.releaseNotes.take(500),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "The APK will be downloaded from your Forgejo server and installed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showUpdateDialog = false
                                viewModel.downloadAndInstallUpdate()
                            }
                        ) { Text("Download & Install") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdateDialog = false }) { Text("Later") }
                    }
                )
            }
        }

        // ─── Backup ──────────────────────────────────────
        item {
            SectionHeader("Encrypted Backup")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "All data is encrypted (NIP-44) and backed up to Nostr relays as kind 30078 events.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.exportBackup() }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Backup Now")
                    }
                }
            }
        }

        // ─── Children ────────────────────────────────────
        item {
            SectionHeader("Children")
        }

        items(children) { child ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(child.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (child.dob != null) {
                            Text(
                                "DOB: ${dateFmt.format(Date(child.dob))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (child.gender != null) {
                            Text(
                                "Gender: ${child.gender}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.deleteChild(child) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showAddChild = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Child")
            }
        }

        // ─── About ────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("About")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Baby Tracker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Version",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "v${viewModel.currentVersionName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "A privacy-first baby tracking app with Nostr-based encrypted backup and parent-to-parent messaging.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your data stays on your device. Backups are end-to-end encrypted. No accounts, no tracking, no cloud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Built with Kotlin, Jetpack Compose, and Material 3.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Add Child dialog
    if (showAddChild) {
        AddChildDialog(
            onDismiss = { showAddChild = false },
            onAdd = { name, dob, gender ->
                viewModel.addChild(name, dob, gender)
                showAddChild = false
            }
        )
    }

    // Import nsec dialog
    if (showImportKey) {
        AlertDialog(
            onDismissRequest = { showImportKey = false; error = null },
            title = { Text("Import Nostr Identity") },
            text = {
                Column {
                    Text("Paste your nsec (private key):", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nsecInput,
                        onValueChange = { nsecInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("nsec1...") }
                    )
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = nostrManager.importIdentity(nsecInput.trim())
                        if (result != null) {
                            showImportKey = false
                            nsecInput = ""
                            error = null
                        } else {
                            error = "Invalid nsec"
                        }
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportKey = false; error = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun AddChildDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, dob: Long?, gender: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Child") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("Date of Birth (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = gender == "boy",
                        onClick = { gender = if (gender == "boy") "" else "boy" },
                        label = { Text("Boy") }
                    )
                    FilterChip(
                        selected = gender == "girl",
                        onClick = { gender = if (gender == "girl") "" else "girl" },
                        label = { Text("Girl") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dobMs = if (dob.isNotBlank()) {
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            sdf.parse(dob)?.time
                        } catch (e: Exception) { null }
                    } else null
                    onAdd(name.trim(), dobMs, gender.ifBlank { null })
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
