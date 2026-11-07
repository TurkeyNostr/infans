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
import androidx.compose.foundation.selection.selectable
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.turkbot.babytracker.R
import com.turkbot.babytracker.ui.theme.ThemePack
import com.turkbot.babytracker.data.entities.Child
import com.turkbot.babytracker.debug.DebugLogger as Dbg
import com.turkbot.babytracker.nostr.NostrManager
import com.turkbot.babytracker.nostr.PartnerStatus
import com.turkbot.babytracker.nostr.RelayMatchResult
import com.turkbot.babytracker.nostr.crypto.SignerType
import com.turkbot.babytracker.nostr.relay.RelayState
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.HapticController
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
    var showQrScanner by remember { mutableStateOf(false) }
    var showMyQr by remember { mutableStateOf(false) }

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
        // ─── Appearance ───────────────────────────────────
        item {
            SectionHeader(stringResource(R.string.settings_appearance))
        }

        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            var selectedTheme by remember { mutableStateOf(ThemePack.load(context)) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_color_theme),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.settings_color_theme_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    ThemePack.entries.forEach { pack ->
                        val onSelect = {
                            selectedTheme = pack
                            ThemePack.save(context, pack)
                            ThemePack.applyIcon(context, pack)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedTheme == pack,
                                    onClick = onSelect
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTheme == pack,
                                onClick = onSelect
                            )
                            Spacer(Modifier.width(8.dp))
                            // Color preview swatches
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = pack.light.primary,
                                modifier = Modifier.size(24.dp)
                            ) {}
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = pack.dark.primary,
                                modifier = Modifier.size(24.dp)
                            ) {}
                            Spacer(Modifier.width(12.dp))
                            Text(
                                pack.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_theme_restart_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ─── Measurement Units ───────────────────────────
        item {
            SectionHeader(stringResource(R.string.settings_measurement_units))
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
                        stringResource(R.string.settings_display_units),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.settings_display_units_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    val systems = listOf("metric" to stringResource(R.string.settings_units_metric_full), "imperial" to stringResource(R.string.settings_units_imperial_full))
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
                            ) { Text(if (value == "metric") stringResource(R.string.settings_metric) else stringResource(R.string.settings_imperial)) }
                        }
                    }
                }
            }
        }

        // ─── Nostr Identity ─────────────────────────────
        item {
            SectionHeader(stringResource(R.string.settings_nostr_identity))
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
                            if (myNip05 != null) stringResource(R.string.settings_your_nip05) else stringResource(R.string.settings_your_npub),
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
                                    if (signer!!.type == SignerType.AMBER) stringResource(R.string.settings_amber_nip55) else stringResource(R.string.settings_local_key)
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.settings_share_identity_desc, if (myNip05 != null) "NIP-05" else "npub"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        // ── Copy npub / NIP-05 to clipboard ──
                        OutlinedButton(
                            onClick = {
                                val identity = myNip05 ?: signer!!.npub
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText("npub", identity)
                                )
                                HapticController.click(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_copy_identity, if (myNip05 != null) "NIP-05" else "npub"))
                        }
                        Spacer(Modifier.height(8.dp))
                        // ── Show QR code for pairing ──
                        OutlinedButton(
                            onClick = { showMyQr = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_show_my_qr))
                        }
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
                                            stringResource(R.string.settings_private_key_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            stringResource(R.string.settings_private_key_warning),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            nsec ?: stringResource(R.string.settings_key_not_available),
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
                                            }) { Text(stringResource(R.string.settings_copy)) }
                                            OutlinedButton(onClick = {
                                                if (nsec != null) {
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, nsec)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_save_nsec)))
                                                }
                                            }) { Text(stringResource(R.string.settings_share)) }
                                            TextButton(onClick = { showNsec = false }) { Text(stringResource(R.string.settings_hide)) }
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
                                    Text(stringResource(R.string.settings_show_private_key))
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
                            Text(stringResource(R.string.settings_log_out_reset))
                        }
                        // ── Offer Amber switch if currently using a local key ──
                        if (signer!!.type != SignerType.AMBER) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                stringResource(R.string.settings_switch_to_amber),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.settings_amber_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    amberError = null
                                    if (amberInstalled) {
                                        scope.launch {
                                            val result = nostrManager.loginWithAmber()
                                            if (result == null) {
                                                amberError = context.getString(R.string.settings_amber_login_failed)
                                            }
                                        }
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://zapstore.dev/apps/com.greenart7c3.amber"))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            amberError = context.getString(R.string.settings_install_amber_zapstore)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (amberInstalled) stringResource(R.string.settings_login_amber) else stringResource(R.string.settings_login_amber_install))
                            }
                            if (!amberInstalled) {
                                Text(
                                    stringResource(R.string.settings_amber_desc),
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
                            stringResource(R.string.settings_no_identity),
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
                                Text(stringResource(R.string.settings_generate_new_key))
                            }
                            OutlinedButton(
                                onClick = { showImportKey = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_import_nsec))
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Button(
                                onClick = {
                                    amberError = null
                                    if (amberInstalled) {
                                        scope.launch {
                                            val result = nostrManager.loginWithAmber()
                                            if (result == null) {
                                                amberError = context.getString(R.string.settings_amber_login_failed)
                                            }
                                        }
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://zapstore.dev/apps/com.greenart7c3.amber"))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            amberError = context.getString(R.string.settings_install_amber_zapstore)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (amberInstalled) stringResource(R.string.settings_login_amber) else stringResource(R.string.settings_login_amber_install))
                            }
                            if (!amberInstalled) {
                                Text(
                                    stringResource(R.string.settings_amber_desc_install),
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
            SectionHeader(stringResource(R.string.settings_partner_sync))
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
                            stringResource(R.string.settings_partner_sync_desc),
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
                                Text(stringResource(R.string.settings_unlink_partner))
                            }
                        } else {
                            OutlinedTextField(
                                value = partnerNpubInput,
                                onValueChange = {
                                    partnerNpubInput = it
                                    partnerError = null
                                },
                                label = { Text(stringResource(R.string.settings_partner_label)) },
                                placeholder = { Text(stringResource(R.string.settings_partner_placeholder)) },
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showQrScanner = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.settings_scan_qr))
                                }
                                Button(
                                    onClick = {
                                        val input = partnerNpubInput.trim()
                                        if (input.isEmpty()) {
                                            partnerError = context.getString(R.string.settings_partner_error_empty)
                                        } else {
                                            scope.launch {
                                                val success = viewModel.setPartnerIdentifier(input)
                                                if (success) {
                                                    partnerNpubInput = ""
                                                    // Trigger a backup so the partner gets our data immediately
                                                    viewModel.exportBackup()
                                                } else {
                                                    partnerError = context.getString(R.string.settings_partner_error_resolve)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.settings_link_partner))
                                }
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.settings_partner_needs_identity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ─── Custom Relays ──────────────────────────────
        item {
            var showRelayEditor by remember { mutableStateOf(false) }
            var relayInput by rememberSaveable { mutableStateOf("") }
            var relayError by remember { mutableStateOf<String?>(null) }

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
                    Text(
                        stringResource(R.string.settings_relays),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.settings_relays_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Current relay list
                    currentRelays.forEach { url ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                url.removePrefix("wss://").removePrefix("ws://"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "(${url.substringBefore("://")})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    if (showRelayEditor) {
                        OutlinedTextField(
                            value = relayInput,
                            onValueChange = {
                                relayInput = it
                                relayError = null
                            },
                            label = { Text(stringResource(R.string.settings_relay_urls_label)) },
                            placeholder = { Text(stringResource(R.string.settings_relay_urls_placeholder)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp),
                            isError = relayError != null,
                            supportingText = relayError?.let { { Text(it) } }
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    val urls = relayInput
                                        .lines()
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .map { url ->
                                            when {
                                                url.startsWith("wss://") || url.startsWith("ws://") -> url
                                                url.startsWith("https://") -> url.replace("https://", "wss://")
                                                url.startsWith("http://") -> url.replace("http://", "ws://")
                                                else -> "wss://$url"
                                            }
                                        }
                                        .distinct()
                                    if (urls.isEmpty()) {
                                        relayError = context.getString(R.string.settings_relay_error_empty)
                                        return@TextButton
                                    }
                                    if (urls.any { !it.startsWith("wss://") && !it.startsWith("ws://") }) {
                                        relayError = context.getString(R.string.settings_relay_error_invalid)
                                        return@TextButton
                                    }
                                    nostrManager.applyCustomRelays(urls)
                                    showRelayEditor = false
                                    relayInput = ""
                                }
                            ) { Text(stringResource(R.string.settings_apply)) }
                            TextButton(
                                onClick = {
                                    showRelayEditor = false
                                    relayInput = ""
                                    relayError = null
                                }
                            ) { Text(stringResource(R.string.settings_cancel)) }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                relayInput = currentRelays.joinToString("\n")
                                showRelayEditor = true
                            }
                        ) { Text(stringResource(R.string.settings_edit_relays)) }
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
                        Text(stringResource(R.string.settings_sync_diagnostic), style = MaterialTheme.typography.bodyLarge)
                        AssistChip(
                            onClick = {},
                            label = { Text(if (relayConnected) stringResource(R.string.settings_connected) else stringResource(R.string.settings_disconnected)) },
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
                            stringResource(R.string.settings_diag_need_partner),
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
                        Text(if (diagRunning) stringResource(R.string.settings_checking) else stringResource(R.string.settings_run_diagnostic))
                    }

                    // Relay overlap
                    if (relayMatch != null) {
                        HorizontalDivider()
                        Text(stringResource(R.string.settings_relay_match), style = MaterialTheme.typography.bodyLarge)
                        if (relayMatch!!.overlap.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_no_shared_relays),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                stringResource(R.string.settings_your_relays, relayMatch!!.myRelays.joinToString { it.removePrefix("wss://") }),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (relayMatch!!.partnerRelays.isEmpty()) {
                                Text(
                                    stringResource(R.string.settings_partner_no_nip65),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    stringResource(R.string.settings_partner_relays, relayMatch!!.partnerRelays.joinToString { it.removePrefix("wss://") }),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.settings_shared_relays_count, relayMatch!!.overlap.size),
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
                        Text(stringResource(R.string.settings_partner_data), style = MaterialTheme.typography.bodyLarge)
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
                                    label = { Text(if (reachable) stringResource(R.string.settings_found) else stringResource(R.string.settings_no_data)) },
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
                        Text(stringResource(R.string.settings_partner_status), style = MaterialTheme.typography.bodyLarge)
                        when (val status = partnerStatus!!) {
                            is PartnerStatus.Mutual -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("\u2713 ", color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        stringResource(R.string.settings_status_mutual),
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
                                        stringResource(R.string.settings_status_different_partner),
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
                                        stringResource(R.string.settings_status_no_infans),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is PartnerStatus.NoPartner -> {
                                Text(
                                    stringResource(R.string.settings_status_no_partner),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        // ─── Haptics ──────────────────────────────────────
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            var hapticsEnabled by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                hapticsEnabled = HapticController.isEnabled(context)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_haptic_feedback),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.settings_haptic_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = {
                            hapticsEnabled = it
                            HapticController.setEnabled(context, it)
                            if (it) HapticController.click(context)
                        }
                    )
                }
            }
        }

        // ─── Privacy ─────────────────────────────────────
        item {
            var screenshotProtection by remember { mutableStateOf(true) }
            val context = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(Unit) {
                screenshotProtection =
                    com.turkbot.babytracker.UiPrefs.screenshotProtectionEnabled(context)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_screenshot_protection),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.settings_screenshot_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = screenshotProtection,
                        onCheckedChange = {
                            screenshotProtection = it
                            com.turkbot.babytracker.UiPrefs.setScreenshotProtection(context, it)
                            // Apply immediately to the current window, not just the next launch.
                            (context as? android.app.Activity)?.window?.let { w ->
                                if (it) {
                                    w.setFlags(
                                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                                    )
                                } else {
                                    w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                                }
                            }
                            if (it) HapticController.click(context)
                        }
                    )
                }
            }
        }

        // ─── Reminders ───────────────────────────────────
        item {
            SectionHeader(stringResource(R.string.settings_reminders))
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
                        stringResource(R.string.settings_feeding_reminder),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.settings_feeding_reminder_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    val intervals = listOf(0 to stringResource(R.string.settings_interval_off), 90 to "1.5h", 120 to "2h", 150 to "2.5h", 180 to "3h", 240 to "4h")
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
            var showJsonExportWarning by remember { mutableStateOf(false) }
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
                                backupMessage = context.getString(R.string.settings_imported_records, count)
                            } else {
                                backupError = context.getString(R.string.settings_import_failed_read)
                            }
                        } catch (e: Exception) {
                            jsonImporting = false
                            backupError = context.getString(R.string.settings_import_failed, e.message ?: "")
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
                        stringResource(R.string.settings_backup_restore),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.settings_backup_restore_desc),
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
                                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_share_pdf)))
                                    } else {
                                        backupError = context.getString(R.string.settings_no_data_to_export)
                                    }
                                } catch (e: Exception) {
                                    pdfExporting = false
                                    backupError = context.getString(R.string.settings_pdf_export_failed, e.message ?: "")
                                }
                            }
                        },
                        enabled = !pdfExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (pdfExporting) stringResource(R.string.settings_exporting) else stringResource(R.string.settings_export_pdf))
                    }
                    // JSON backup export
                    OutlinedButton(
                        onClick = { showJsonExportWarning = true },
                        enabled = !jsonExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (jsonExporting) stringResource(R.string.settings_exporting) else stringResource(R.string.settings_export_json))
                    }
                    // JSON restore
                    OutlinedButton(
                        onClick = {
                            jsonFilePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        enabled = !jsonImporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (jsonImporting) stringResource(R.string.settings_importing) else stringResource(R.string.settings_restore_json))
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

            if (showJsonExportWarning) {
                AlertDialog(
                    onDismissRequest = { showJsonExportWarning = false },
                    title = { Text(stringResource(R.string.settings_json_not_encrypted_title)) },
                    text = {
                        Text(
                            stringResource(R.string.settings_json_not_encrypted_body)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showJsonExportWarning = false
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
                                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_save_json)))
                                        } else {
                                            backupError = context.getString(R.string.settings_no_data_to_export)
                                        }
                                    } catch (e: Exception) {
                                        jsonExporting = false
                                        backupError = context.getString(R.string.settings_json_export_failed, e.message ?: "")
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.settings_export_anyway)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showJsonExportWarning = false }) { Text(stringResource(R.string.settings_cancel)) }
                    }
                )
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
                        stringResource(R.string.settings_delete_relay_data),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        stringResource(R.string.settings_delete_relay_data_desc),
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
                        Text(if (deleting) stringResource(R.string.settings_deleting) else stringResource(R.string.settings_delete_from_relays))
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
                    title = { Text(stringResource(R.string.settings_delete_relay_confirm_title)) },
                    text = { Text(stringResource(R.string.settings_delete_relay_confirm_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                deleting = true
                                deleteResult = null
                                scope.launch {
                                    val ok = viewModel.deleteRelayData()
                                    deleting = false
                                    deleteResult = if (ok) context.getString(R.string.settings_relay_deleted)
                                                   else context.getString(R.string.settings_relay_delete_failed)
                                }
                            }
                        ) { Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.settings_cancel)) }
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
                    Text(stringResource(R.string.settings_debug_log), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_debug_log_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showLog = !showLog }) {
                            Text(if (showLog) stringResource(R.string.settings_hide_log) else stringResource(R.string.settings_show_log))
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
                                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_share_debug_log)))
                                    }
                                }
                            },
                            enabled = !logExporting && logEntries.isNotEmpty()
                        ) {
                            Text(if (logExporting) stringResource(R.string.settings_exporting) else stringResource(R.string.settings_export))
                        }
                        OutlinedButton(
                            onClick = { Dbg.clear() },
                            enabled = logEntries.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.settings_clear))
                        }
                    }
                    if (showLog) {
                        Spacer(Modifier.height(12.dp))
                        if (logEntries.isEmpty()) {
                            Text(stringResource(R.string.settings_no_log_entries), style = MaterialTheme.typography.bodySmall)
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
            SectionHeader(stringResource(R.string.settings_app_updates))
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
                        stringResource(R.string.settings_auto_update),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.settings_auto_update_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.settings_check_auto))
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
                            stringResource(R.string.settings_current_version),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.settings_version_value, viewModel.currentVersionName),
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
                                stringResource(R.string.settings_latest_version),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.settings_version_value, updateInfo!!.versionName),
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
                            Text(stringResource(R.string.settings_checking_updates), style = MaterialTheme.typography.bodySmall)
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
                                stringResource(R.string.settings_update_available, updateInfo!!.versionName),
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
                            stringResource(R.string.settings_last_checked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.checkForUpdate() },
                            enabled = !updateChecking
                        ) { Text(stringResource(R.string.settings_check_now)) }
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
                                    Text(stringResource(R.string.settings_download_install))
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
                    title = { Text(stringResource(R.string.settings_update_to_version, updateInfo!!.versionName)) },
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
                                stringResource(R.string.settings_update_dialog_desc),
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
                        ) { Text(stringResource(R.string.settings_download_install)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdateDialog = false }) { Text(stringResource(R.string.settings_later)) }
                    }
                )
            }
        }

        // ─── Backup ──────────────────────────────────────
        item {
            SectionHeader(stringResource(R.string.settings_encrypted_backup))
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
                        stringResource(R.string.settings_encrypted_backup_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.exportBackup() }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_backup_now))
                    }
                }
            }
        }

        // ─── Children ────────────────────────────────────
        item {
            SectionHeader(stringResource(R.string.settings_children))
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
                                stringResource(R.string.settings_dob, dateFmt.format(Date(child.dob))),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (child.gender != null) {
                            Text(
                                stringResource(R.string.settings_gender, child.gender ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.deleteChild(child) }) {
                        Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
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
                Text(stringResource(R.string.settings_add_child))
            }
        }

        // ─── Help ─────────────────────────────────────────
        item {
            SectionHeader(stringResource(R.string.settings_help))
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
                        stringResource(R.string.settings_setup_wizard),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.settings_setup_wizard_desc),
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
                        Text(stringResource(R.string.settings_show_setup_wizard))
                    }
                }
            }
        }

        // ─── About ────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.settings_about))
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
                            stringResource(R.string.settings_version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.settings_version_value, viewModel.currentVersionName),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.settings_about_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_about_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_about_built_with),
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
            title = { Text(stringResource(R.string.settings_import_identity_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_paste_nsec), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nsecInput,
                        onValueChange = { nsecInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.settings_nsec_placeholder)) }
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
                            error = context.getString(R.string.settings_invalid_nsec)
                        }
                    }
                }) { Text(stringResource(R.string.settings_import)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportKey = false; error = null }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }

    // ── QR Scanner overlay ──
    if (showQrScanner) {
        com.turkbot.babytracker.ui.components.QrScanner(
            onScanned = { result ->
                showQrScanner = false
                partnerNpubInput = result
                // Auto-attempt link after scan
                scope.launch {
                    val success = viewModel.setPartnerIdentifier(result.trim())
                    if (success) {
                        partnerNpubInput = ""
                        viewModel.exportBackup()
                    } else {
                        partnerError = context.getString(R.string.settings_partner_error_qr)
                    }
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }

    // ── Show My QR dialog ──
    if (showMyQr && signer != null) {
        val identity = nostrManager.signer.value?.let {
            nostrManager.myNip05.value ?: it.npub
        } ?: ""
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMyQr = false },
            confirmButton = {
                TextButton(onClick = { showMyQr = false }) { Text(stringResource(R.string.settings_close)) }
            },
            title = { Text(stringResource(R.string.settings_your_qr)) },
            text = {
                com.turkbot.babytracker.ui.components.QrCodeDisplay(
                    content = identity,
                    caption = stringResource(R.string.settings_qr_caption)
                )
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
        title = { Text(stringResource(R.string.settings_add_child)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text(stringResource(R.string.settings_dob_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = gender == "boy",
                        onClick = { gender = if (gender == "boy") "" else "boy" },
                        label = { Text(stringResource(R.string.settings_boy)) }
                    )
                    FilterChip(
                        selected = gender == "girl",
                        onClick = { gender = if (gender == "girl") "" else "girl" },
                        label = { Text(stringResource(R.string.settings_girl)) }
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
            ) { Text(stringResource(R.string.settings_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        }
    )
}
