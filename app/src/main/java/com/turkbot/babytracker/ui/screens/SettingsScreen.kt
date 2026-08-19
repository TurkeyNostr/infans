package com.turkbot.babytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.Child
import com.turkbot.babytracker.nostr.NostrManager
import com.turkbot.babytracker.nostr.crypto.NostrKeys
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(viewModel: BabyViewModel, nostrManager: NostrManager) {
    val children by viewModel.children.collectAsState()
    val keys by nostrManager.keys.collectAsState()
    val relayConnected by nostrManager.relayConnected.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddChild by remember { mutableStateOf(false) }
    var showImportKey by remember { mutableStateOf(false) }
    var nsecInput by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ─── Nostr Identity ─────────────────────────────
        item {
            Text("Nostr Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (keys != null) {
                        Text("Your npub:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(keys!!.npub, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Share this with the other parent so they can send you messages.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("No Nostr identity yet.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.generateNostrIdentity() }) {
                                Text("Generate New")
                            }
                            OutlinedButton(onClick = { showImportKey = true }) {
                                Text("Import nsec")
                            }
                        }
                    }
                }
            }
        }

        // Relay status
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Relay Status")
                    Text(
                        if (relayConnected) "🟢 Connected" else "🔴 Disconnected",
                        color = if (relayConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ─── Backup ──────────────────────────────────────
        item {
            Text("Encrypted Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("All data is encrypted (NIP-44) and backed up to Nostr relays as kind 30078 events.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
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
            Text("Children", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(children) { child ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(child.name, style = MaterialTheme.typography.bodyLarge)
                        if (child.dob != null) {
                            Text("DOB: ${dateFmt.format(Date(child.dob))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (child.gender != null) {
                            Text("Gender: ${child.gender}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { viewModel.deleteChild(child) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = { showAddChild = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Child")
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
