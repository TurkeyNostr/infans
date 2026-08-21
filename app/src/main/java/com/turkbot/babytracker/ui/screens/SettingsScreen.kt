/**
 * Baby Tracker — Native Android (Kotlin)
 *
 * A privacy-first baby tracking app with Nostr-based encrypted storage
 * and parent-to-parent sync.
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turkbot.babytracker.data.entities.Child
import com.turkbot.babytracker.debug.DebugLogger as Dbg
import com.turkbot.babytracker.nostr.NostrManager
import com.turkbot.babytracker.nostr.PartnerStatus
import com.turkbot.babytracker.nostr.RelayMatchResult
import com.turkbot.babytracker.nostr.crypto.SignerType
import com.turkbot.babytracker.nostr.relay.RelayState
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: BabyViewModel, nostrManager: NostrManager, onReplayOnboarding: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val children by viewModel.children.collectAsState()
    val signer by nostrManager.signer.collectAsState()
    val relayConnected by nostrManager.relayConnected.collectAsState()
    val currentRelays by nostrManager.currentRelays.collectAsState()
    val partnerNpubState by viewModel.partnerNpub.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddChild by remember { mutableStateOf(false) }
    var showImportKey by remember { mutableStateOf(false) }
    var showNsec by remember { mutableStateOf(false) }
    var nsecInput by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var amberError by rememberSaveable { mutableStateOf<String?>(null) }
    var partnerNpubInput by rememberSaveable { mutableStateOf("") }
    var partnerError by rememberSaveable { mutableStateOf<String?>(null) }

    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    var amberInstalled by remember { mutableStateOf(viewModel.isAmberInstalled()) }

    // Re-check Amber installation when Settings gains focus
    // (e.g. returning from Amber after install)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                amberInstalled = viewModel.isAmberInstalled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                        val myNip05 by nostrManager.myNip05.collectAsState()
                        Text(
                            if (myNip05 != null) "Your NIP-05" else "Your npub",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            myNip05 ?: signer!!.npub,
                            style = MaterialTheme.typography.bodySmall
                        )
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
                            "Share this ${if (myNip05 != null) "NIP-05" else "npub"} with the other parent so they can sync data with you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        // ── Export private key (local keys only) ──
                        if (signer!!.type != SignerType.AMBER) {
                            if (showNsec) {
                                val nsec = remember(showNsec) { nostrManager.getLocalNsec() }
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Your Private Key (nsec)",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Anyone with this key can read your encrypted backups. Store it somewhere safe. Do not share it with anyone.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            nsec ?: "Key not available",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = {
                                                if (nsec != null) {
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("nsec", nsec))
                                                }
                                            }) { Text("Copy") }
                                            OutlinedButton(onClick = {
                                                if (nsec != null) {
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, nsec)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Save nsec"))
                                                }
                                            }) { Text("Share") }
                                            TextButton(onClick = { showNsec = false }) { Text("Hide") }
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showNsec = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Show My Private Key")
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
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
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://zapstore.dev/apps/com.greenart7c3.amber"))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            amberError = "Install Amber from Zapstore: zapstore.dev/apps/com.greenart7c3.amber"
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
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://zapstore.dev/apps/com.greenart7c3.amber"))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            amberError = "Install Amber from Zapstore: zapstore.dev/apps/com.greenart7c3.amber"
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
                                    "Amber is a Nostr signer app — your private key stays in Amber, this app never sees it. Tap above to install from Zapstore.",
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
                        val partnerNip05 by viewModel.partnerNip05.collectAsState()
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
                                Column {
                                    Text(
                                        partnerNip05 ?: (currentPartner!!.take(24) + "..."),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
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
                                label = { Text("Partner's npub or NIP-05") },
                                placeholder = { Text("npub1... or name@domain.com") },
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
                                    if (input.isEmpty()) {
                                        partnerError = "Enter an npub or NIP-05 identifier"
                                    } else {
                                        scope.launch {
                                            val success = viewModel.setPartnerIdentifier(input)
                                            if (success) {
                                                partnerNpubInput = ""
                                                // Trigger a backup so the partner gets our data immediately
                                                viewModel.exportBackup()
                                            } else {
                                                partnerError = "Could not resolve — check the identifier"
                                            }
                                        }
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

        // ─── Sync Diagnostic ─────────────────────────────
        item {
            var diagRunning by remember { mutableStateOf(false) }
            var relayMatch by remember { mutableStateOf<RelayMatchResult?>(null) }
            var partnerReachable by remember { mutableStateOf<Map<String, Boolean>?>(null) }
            var partnerStatus by remember { mutableStateOf<PartnerStatus?>(null) }
            val scope = rememberCoroutineScope()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header + overall connection chip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sync Diagnostic", style = MaterialTheme.typography.bodyLarge)
                        AssistChip(
                            onClick = {},
                            label = { Text(if (relayConnected) "Connected" else "Disconnected") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (relayConnected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer,
                                labelColor = if (relayConnected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }

                    // Per-relay connection states (always visible)
                    nostrManager.relayStates().forEach { (url, state) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = url.removePrefix("wss://"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(when (state) {
                                        RelayState.CONNECTED -> "\u2713"
                                        RelayState.CONNECTING -> "\u2026"
                                        RelayState.ERROR -> "\u2717"
                                        RelayState.DISCONNECTED -> "\u2014"
                                    })
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = when (state) {
                                        RelayState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                                        RelayState.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.errorContainer
                                    },
                                    labelColor = when (state) {
                                        RelayState.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                                        RelayState.CONNECTING -> MaterialTheme.colorScheme.onSecondaryContainer
                                        else -> MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            )
                        }
                    }

                    if (partnerNpubState == null) {
                        Text(
                            "Set partner npub to run a full diagnostic.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Single button to run all checks
                    Button(
                        onClick = {
                            diagRunning = true
                            relayMatch = null
                            partnerReachable = null
                            partnerStatus = null
                            scope.launch {
                                // Run all three checks in parallel
                                val matchDef = async { nostrManager.checkPartnerRelayMatch() }
                                val reachDef = async { nostrManager.checkPartnerReachable() }
                                val statusDef = async { nostrManager.checkPartnerStatus() }
                                relayMatch = matchDef.await()
                                partnerReachable = reachDef.await()
                                partnerStatus = statusDef.await()
                                diagRunning = false
                            }
                        },
                        enabled = !diagRunning && relayConnected && partnerNpubState != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (diagRunning) "Checking\u2026 (5s)" else "Run Diagnostic")
                    }

                    // Relay overlap
                    if (relayMatch != null) {
                        HorizontalDivider()
                        Text("Relay Match", style = MaterialTheme.typography.bodyLarge)
                        if (relayMatch!!.overlap.isEmpty()) {
                            Text(
                                "\u26a0 No shared relays \u2014 you and your partner are on different relays.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Your relays: ${relayMatch!!.myRelays.joinToString { it.removePrefix("wss://") }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (relayMatch!!.partnerRelays.isEmpty()) {
                                Text(
                                    "Partner: no NIP-65 relay list found",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    "Partner: ${relayMatch!!.partnerRelays.joinToString { it.removePrefix("wss://") }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                "\u2713 ${relayMatch!!.overlap.size} shared relay(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            relayMatch!!.overlap.forEach { url ->
                                Text(
                                    "  \u2713 ${url.removePrefix("wss://")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Partner data on relays
                    if (partnerReachable != null) {
                        HorizontalDivider()
                        Text("Partner Data on Relays", style = MaterialTheme.typography.bodyLarge)
                        partnerReachable!!.forEach { (url, reachable) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = url.removePrefix("wss://"),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text(if (reachable) "Found" else "No data") },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (reachable)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.errorContainer,
                                        labelColor = if (reachable)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                )
                            }
                        }
                    }

                    // Partner status
                    if (partnerStatus != null) {
                        HorizontalDivider()
                        Text("Partner Status", style = MaterialTheme.typography.bodyLarge)
                        when (val status = partnerStatus!!) {
                            is PartnerStatus.Mutual -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("\u2713 ", color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "Mutual \u2014 your partner has you configured. Data should sync.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            is PartnerStatus.HasDifferentPartner -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("\u26a0 ", color = MaterialTheme.colorScheme.error)
                                    Text(
                                        "Your partner runs Infans but does NOT have you as their partner.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            is PartnerStatus.NoInfansData -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("? ", color = MaterialTheme.colorScheme.tertiary)
                                    Text(
                                        "No Infans data found. They may not be running Infans, or use different relays.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is PartnerStatus.NoPartner -> {
                                Text(
                                    "No partner configured.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
                    val intervals = listOf(0 to "Off", 90 to "1.5h", 120 to "2h", 150 to "2.5h", 180 to "3h", 240 to "4h")
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

        // ─── Backup & Restore ──────────────────────────────
        item {
            var pdfExporting by remember { mutableStateOf(false) }
            var jsonExporting by remember { mutableStateOf(false) }
            var jsonImporting by remember { mutableStateOf(false) }
            var backupError by remember { mutableStateOf<String?>(null) }
            var backupMessage by remember { mutableStateOf<String?>(null) }
            val jsonFilePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    jsonImporting = true
                    backupError = null
                    backupMessage = null
                    scope.launch {
                        try {
                            val count = viewModel.importFromJson(uri)
                            jsonImporting = false
                            if (count >= 0) {
                                backupMessage = "✓ Imported $count records"
                            } else {
                                backupError = "Import failed: could not read file"
                            }
                        } catch (e: Exception) {
                            jsonImporting = false
                            backupError = "Import failed: ${e.message}"
                        }
                    }
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
                        "Backup & Restore",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Export data for safekeeping or restore after relay failure or accidental deletion. PDF is for reading/printing. JSON backup can be re-imported to fully restore all data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    // PDF export
                    Button(
                        onClick = {
                            pdfExporting = true
                            backupError = null
                            backupMessage = null
                            scope.launch {
                                try {
                                    val uri = viewModel.exportToPdf(context)
                                    pdfExporting = false
                                    if (uri != null) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                                    } else {
                                        backupError = "No data to export"
                                    }
                                } catch (e: Exception) {
                                    pdfExporting = false
                                    backupError = "PDF export failed: ${e.message}"
                                }
                            }
                        },
                        enabled = !pdfExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (pdfExporting) "Exporting…" else "Export to PDF")
                    }
                    // JSON backup export
                    OutlinedButton(
                        onClick = {
                            jsonExporting = true
                            backupError = null
                            backupMessage = null
                            scope.launch {
                                try {
                                    val uri = viewModel.exportToJson(context)
                                    jsonExporting = false
                                    if (uri != null) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Save JSON Backup"))
                                    } else {
                                        backupError = "No data to export"
                                    }
                                } catch (e: Exception) {
                                    jsonExporting = false
                                    backupError = "JSON export failed: ${e.message}"
                                }
                            }
                        },
                        enabled = !jsonExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (jsonExporting) "Exporting…" else "Export JSON Backup")
                    }
                    // JSON restore
                    OutlinedButton(
                        onClick = {
                            jsonFilePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        enabled = !jsonImporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (jsonImporting) "Importing…" else "Restore from JSON")
                    }
                    backupMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    backupError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // ─── Delete Relay Data ──────────────────────────────
        item {
            var deleting by remember { mutableStateOf(false) }
            var deleteResult by remember { mutableStateOf<String?>(null) }
            var showDeleteConfirm by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Delete Relay Data",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Removes all encrypted backups and partner-sync events from Nostr relays. Your local data is not affected. This signs two empty replacement events (one for self-backup, one for partner sync) that overwrite the old data on each relay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { showDeleteConfirm = true },
                        enabled = !deleting && relayConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (deleting) "Deleting…" else "Delete from Relays")
                    }
                    deleteResult?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Relay Data?") },
                    text = { Text("This will publish empty replacement events to all connected relays, overwriting your encrypted backups. Your local data stays intact. This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                deleting = true
                                deleteResult = null
                                scope.launch {
                                    val ok = viewModel.deleteRelayData()
                                    deleting = false
                                    deleteResult = if (ok) "✓ Relay data deleted"
                                                   else "Failed to delete — check relay connection"
                                }
                            }
                        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                    }
                )
            }
        }

        // ─── Debug Log ───────────────────────────────────
        item {
            var showLog by remember { mutableStateOf(false) }
            val logEntries by com.turkbot.babytracker.debug.DebugLogger.entries.collectAsState()
            var logExporting by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Debug Log", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "PII-free diagnostic log — no npubs, pubkeys, or personal data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showLog = !showLog }) {
                            Text(if (showLog) "Hide Log" else "Show Log")
                        }
                        OutlinedButton(
                            onClick = {
                                logExporting = true
                                scope.launch {
                                    val uri = Dbg.export(context)
                                    logExporting = false
                                    uri?.let {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, it)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Debug Log"))
                                    }
                                }
                            },
                            enabled = !logExporting && logEntries.isNotEmpty()
                        ) {
                            Text(if (logExporting) "Exporting…" else "Export")
                        }
                        OutlinedButton(
                            onClick = { Dbg.clear() },
                            enabled = logEntries.isNotEmpty()
                        ) {
                            Text("Clear")
                        }
                    }
                    if (showLog) {
                        Spacer(Modifier.height(12.dp))
                        if (logEntries.isEmpty()) {
                            Text("No log entries yet", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(logEntries.reversed()) { entry ->
                                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                                        .format(java.util.Date(entry.timestamp))
                                    val color = when (entry.level) {
                                        Dbg.Level.ERROR -> MaterialTheme.colorScheme.error
                                        Dbg.Level.WARN -> MaterialTheme.colorScheme.tertiary
                                        Dbg.Level.INFO -> MaterialTheme.colorScheme.onSurface
                                    }
                                    Text(
                                        "$time [${entry.category.name}] ${entry.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = color,
                                        fontSize = 11.sp
                                    )
                                }
                            }
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
                        "Check GitHub for new versions and install them automatically.",
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
                                "The APK will be downloaded from GitHub and installed.",
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

        // ─── Help ─────────────────────────────────────────
        item {
            SectionHeader("Help")
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
                        "Setup Wizard",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Replay the onboarding tutorial if you missed a step or want to review the setup options.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onReplayOnboarding,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Show Setup Wizard")
                    }
                }
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
                        "Infans",
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
                        "A privacy-first baby tracking app with Nostr-based encrypted backup and parent-to-parent sync.",
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
