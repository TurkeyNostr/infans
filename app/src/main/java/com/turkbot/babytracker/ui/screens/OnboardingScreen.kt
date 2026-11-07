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

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.turkbot.babytracker.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.nostr.NostrManager
import com.turkbot.babytracker.util.UnitPreferences
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ──────────────────────────────────────────────────────────────────────────────
// Onboarding state — which page we're on and what the user picked
// ──────────────────────────────────────────────────────────────────────────────

private enum class OnboardPage {
    WELCOME, ADD_CHILD, UNITS, CHOOSE_SYNC, SET_UP_KEY, PAIR_PARTNER, DONE
}

private enum class SyncMode(@StringRes val labelRes: Int, val icon: ImageVector, @StringRes val descRes: Int) {
    OFFLINE(
        R.string.onboarding_mode_offline,
        Icons.Filled.CloudOff,
        R.string.onboarding_mode_offline_desc
    ),
    BACKUP(
        R.string.onboarding_mode_backup,
        Icons.Filled.Key,
        R.string.onboarding_mode_backup_desc
    ),
    PAIR(
        R.string.onboarding_mode_pair,
        Icons.Filled.People,
        R.string.onboarding_mode_pair_desc
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Entry point — checks SharedPreferences, shows onboarding or the main app
// ──────────────────────────────────────────────────────────────────────────────

private const val PREF_ONBOARDING_DONE = "onboarding_complete"

fun isOnboardingComplete(context: Context): Boolean =
    context.getSharedPreferences("baby_tracker_prefs", Context.MODE_PRIVATE)
        .getBoolean(PREF_ONBOARDING_DONE, false)

fun setOnboardingComplete(context: Context) {
    context.getSharedPreferences("baby_tracker_prefs", Context.MODE_PRIVATE)
        .edit().putBoolean(PREF_ONBOARDING_DONE, true).apply()
}

@Composable
fun OnboardingScreen(
    viewModel: BabyViewModel,
    nostrManager: NostrManager,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf(OnboardPage.WELCOME) }
    var syncMode by remember { mutableStateOf(SyncMode.OFFLINE) }

    // ── Add-child form state ──
    var childName by rememberSaveable { mutableStateOf("") }
    var childDob by rememberSaveable { mutableStateOf("") }
    var childGender by rememberSaveable { mutableStateOf("") }

    // ── Key setup state ──
    var keyBusy by remember { mutableStateOf(false) }
    var keyError by rememberSaveable { mutableStateOf<String?>(null) }
    var hasAmber by remember { mutableStateOf(viewModel.isAmberInstalled()) }
    val signer by nostrManager.signer.collectAsState()

    // ── Partner pairing state ──
    var partnerInput by rememberSaveable { mutableStateOf("") }
    var partnerError by rememberSaveable { mutableStateOf<String?>(null) }
    var partnerBusy by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    val partnerNpub by viewModel.partnerNpub.collectAsState()

    // ── Units state ──
    var unitSystem by remember { mutableStateOf(UnitPreferences.getSystem(context)) }

    Scaffold { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Progress indicator + skip ──
                OnboardingHeader(
                    currentPage = page,
                    onSkip = {
                        setOnboardingComplete(context)
                        onComplete()
                    }
                )

                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "onboard"
                ) { current ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
                    ) {
                    when (current) {
                        OnboardPage.WELCOME -> item { WelcomePage { page = OnboardPage.ADD_CHILD } }
                        OnboardPage.ADD_CHILD -> item {
                            AddChildPage(
                                name = childName, onName = { childName = it },
                                dob = childDob, onDob = { childDob = it },
                                gender = childGender, onGender = { childGender = it },
                                onBack = { page = OnboardPage.WELCOME },
                                onNext = { page = OnboardPage.UNITS },
                                onSkip = { page = OnboardPage.UNITS }
                            )
                        }
                        OnboardPage.UNITS -> item {
                            UnitsPage(
                                unitSystem = unitSystem,
                                onSystem = { unitSystem = it },
                                onBack = { page = OnboardPage.ADD_CHILD },
                                onNext = { page = OnboardPage.CHOOSE_SYNC },
                                onSkip = { page = OnboardPage.CHOOSE_SYNC }
                            )
                        }
                        OnboardPage.CHOOSE_SYNC -> item {
                            ChooseSyncPage(
                                selected = syncMode,
                                onSelect = { syncMode = it },
                                onBack = { page = OnboardPage.UNITS },
                                onNext = {
                                    page = when (syncMode) {
                                        SyncMode.OFFLINE -> OnboardPage.DONE
                                        SyncMode.BACKUP,
                                        SyncMode.PAIR -> OnboardPage.SET_UP_KEY
                                    }
                                },
                                onSkip = {
                                    page = when (syncMode) {
                                        SyncMode.OFFLINE -> OnboardPage.DONE
                                        SyncMode.BACKUP,
                                        SyncMode.PAIR -> OnboardPage.SET_UP_KEY
                                    }
                                }
                            )
                        }
                        OnboardPage.SET_UP_KEY -> item {
                            SetUpKeyPage(
                                busy = keyBusy,
                                error = keyError,
                                signerActive = signer != null,
                                isLocalKey = signer?.type == com.turkbot.babytracker.nostr.crypto.SignerType.LOCAL,
                                hasAmber = hasAmber,
                                onBack = { page = OnboardPage.CHOOSE_SYNC },
                                onSkip = { page = OnboardPage.DONE },
                                onGenerate = {
                                    scope.launch {
                                        keyBusy = true
                                        keyError = null
                                        viewModel.generateNostrIdentity()
                                        kotlinx.coroutines.delay(500)
                                        keyBusy = false
                                        val ok = signer != null || nostrManager.signer.value != null
                                        if (ok) {
                                            page = if (syncMode == SyncMode.PAIR)
                                                OnboardPage.PAIR_PARTNER else OnboardPage.DONE
                                        } else {
                                            keyError = context.getString(R.string.onboarding_key_error_generate)
                                        }
                                    }
                                },
                                onAmber = {
                                    scope.launch {
                                        keyBusy = true
                                        keyError = null
                                        val result = nostrManager.loginWithAmber()
                                        keyBusy = false
                                        if (result != null) {
                                            page = if (syncMode == SyncMode.PAIR)
                                                OnboardPage.PAIR_PARTNER else OnboardPage.DONE
                                        } else {
                                            keyError = context.getString(R.string.onboarding_key_error_amber)
                                        }
                                    }
                                }
                            )
                        }
                        OnboardPage.PAIR_PARTNER -> item {
                            PairPartnerPage(
                                input = partnerInput,
                                onInput = { partnerInput = it; partnerError = null },
                                error = partnerError,
                                busy = partnerBusy,
                                partnerLinked = partnerNpub != null,
                                signer = signer,
                                onScanQr = { showQrScanner = true },
                                onBack = { page = OnboardPage.SET_UP_KEY },
                                onSkip = { page = OnboardPage.DONE },
                                onLink = {
                                    scope.launch {
                                        partnerBusy = true
                                        partnerError = null
                                        val input = partnerInput.trim()
                                        if (input.isEmpty()) {
                                            partnerError = context.getString(R.string.onboarding_partner_error_empty)
                                            partnerBusy = false
                                            return@launch
                                        }
                                        val success = viewModel.setPartnerIdentifier(input)
                                        partnerBusy = false
                                        if (success) {
                                            page = OnboardPage.DONE
                                        } else {
                                            partnerError = context.getString(R.string.onboarding_partner_error_resolve)
                                        }
                                    }
                                }
                            )
                        }
                        OnboardPage.DONE -> item {
                            DonePage(
                                syncMode = syncMode,
                                signerActive = signer != null,
                                partnerLinked = partnerNpub != null,
                                onFinish = {
                                    // Save the child if a name was entered
                                    if (childName.isNotBlank()) {
                                        val dobMs = if (childDob.isNotBlank()) {
                                            try {
                                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                    .parse(childDob)?.time
                                            } catch (e: Exception) { null }
                                        } else null
                                        viewModel.addChild(
                                            childName.trim(),
                                            dobMs,
                                            childGender.ifBlank { null }
                                        )
                                    }
                                    // Save unit preference
                                    UnitPreferences.setSystem(context, unitSystem)
                                    // Mark onboarding complete
                                    setOnboardingComplete(context)
                                    onComplete()
                                }
                            )
                        }
                    }
                }
                }
            }
        }
    }

    // ── QR Scanner overlay (used in pair partner page) ──
    if (showQrScanner) {
        com.turkbot.babytracker.ui.components.QrScanner(
            onScanned = { result ->
                showQrScanner = false
                partnerInput = result
                // Auto-attempt link after scan
                scope.launch {
                    partnerBusy = true
                    partnerError = null
                    val success = viewModel.setPartnerIdentifier(result.trim())
                    partnerBusy = false
                    if (success) {
                        page = OnboardPage.DONE
                    } else {
                        partnerError = context.getString(R.string.onboarding_partner_error_qr)
                    }
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Progress header — shows step dots + skip button
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingHeader(currentPage: OnboardPage, onSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step progress dots
        val pages = OnboardPage.entries
        val currentIndex = pages.indexOf(currentPage)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            pages.forEachIndexed { index, _ ->
                val isActive = index <= currentIndex
                Surface(
                    shape = CircleShape,
                    color = if (isActive)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(8.dp)
                ) {}
            }
        }
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.onboarding_skip_all), style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Page 1 — Welcome
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.ChildCare,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Infans",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_welcome_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrivacyRow(Icons.Filled.Lock, stringResource(R.string.onboarding_privacy_data))
                PrivacyRow(Icons.Filled.CloudOff, stringResource(R.string.onboarding_privacy_no_accounts))
                PrivacyRow(Icons.Filled.Done, stringResource(R.string.onboarding_privacy_offline))
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(stringResource(R.string.onboarding_get_started), fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun PrivacyRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Page 2 — Add Child
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddChildPage(
    name: String, onName: (String) -> Unit,
    dob: String, onDob: (String) -> Unit,
    gender: String, onGender: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PageHeader(stringResource(R.string.onboarding_add_child_title), stringResource(R.string.onboarding_add_child_subtitle))
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.onboarding_child_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = dob,
            onValueChange = onDob,
            label = { Text(stringResource(R.string.onboarding_dob_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_optional)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_gender_label), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = gender == "boy",
                onClick = { onGender(if (gender == "boy") "" else "boy") },
                label = { Text(stringResource(R.string.onboarding_boy)) }
            )
            FilterChip(
                selected = gender == "girl",
                onClick = { onGender(if (gender == "girl") "" else "girl") },
                label = { Text(stringResource(R.string.onboarding_girl)) }
            )
        }

        Spacer(Modifier.height(32.dp))
        NavButtons(
            onBack = onBack,
            onNext = onNext,
            nextEnabled = name.isNotBlank(),
            nextLabel = stringResource(R.string.onboarding_next),
            onSkip = onSkip
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Page 3 — Units
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnitsPage(
    unitSystem: UnitPreferences.System,
    onSystem: (UnitPreferences.System) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PageHeader(stringResource(R.string.onboarding_measurement_units), stringResource(R.string.onboarding_units_subtitle))
        Spacer(Modifier.height(16.dp))

        val systems = listOf(
            UnitPreferences.System.METRIC to stringResource(R.string.settings_units_metric_full),
            UnitPreferences.System.IMPERIAL to stringResource(R.string.settings_units_imperial_full)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            systems.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = unitSystem == value,
                    onClick = { onSystem(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, systems.size)
                ) { Text(if (value == UnitPreferences.System.METRIC) stringResource(R.string.settings_metric) else stringResource(R.string.settings_imperial)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (unitSystem == UnitPreferences.System.METRIC)
                stringResource(R.string.onboarding_units_metric_desc)
            else
                stringResource(R.string.onboarding_units_imperial_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))
        NavButtons(onBack = onBack, onNext = onNext, nextEnabled = true, nextLabel = stringResource(R.string.onboarding_next), onSkip = onSkip)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Page 4 — Choose Sync Mode (simplified language)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChooseSyncPage(
    selected: SyncMode,
    onSelect: (SyncMode) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PageHeader(stringResource(R.string.onboarding_sync_title), stringResource(R.string.onboarding_sync_subtitle))
        Spacer(Modifier.height(16.dp))

        SyncMode.entries.forEach { mode ->
            ModeCard(
                mode = mode,
                selected = selected == mode,
                onClick = { onSelect(mode) }
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))
        NavButtons(onBack = onBack, onNext = onNext, nextEnabled = true, nextLabel = stringResource(R.string.onboarding_next), onSkip = onSkip)
    }
}

@Composable
private fun ModeCard(mode: SyncMode, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected)
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
    else Modifier

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(border),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                mode.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (selected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(mode.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (selected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(mode.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Done,
                    contentDescription = stringResource(R.string.onboarding_selected_cd),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Page 5 — Set Up Key (frictionless: one-tap generate, jargon-free)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetUpKeyPage(
    busy: Boolean,
    error: String?,
    signerActive: Boolean,
    isLocalKey: Boolean,
    hasAmber: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onGenerate: () -> Unit,
    onAmber: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PageHeader(stringResource(R.string.onboarding_key_title), stringResource(R.string.onboarding_key_subtitle))
        Spacer(Modifier.height(16.dp))

        if (signerActive) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Done, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.onboarding_key_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium)
                }
            }

            // ── Key safety warning for locally-generated keys ──
            if (isLocalKey) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                stringResource(R.string.onboarding_key_safe_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            stringResource(R.string.onboarding_key_safe_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            NavButtons(
                onBack = onBack,
                onNext = onSkip,
                nextEnabled = true,
                nextLabel = stringResource(R.string.onboarding_continue)
            )
            return@Column
        }

        // Primary: one-tap generate
        Card(
            onClick = { if (!busy) onGenerate() },
            modifier = Modifier.fillMaxWidth().border(
                2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.onboarding_create_key),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.onboarding_create_key_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp), strokeWidth = 2.dp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Alternative: Amber (external signer)
        OutlinedButton(
            onClick = onAmber,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.AccountCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (hasAmber) stringResource(R.string.onboarding_use_amber) else stringResource(R.string.onboarding_use_amber_install))
        }
        if (!hasAmber) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.onboarding_amber_install_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.onboarding_back))
            }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip_for_now)) }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Page 6 — Pair Partner (QR-first, also supports npub/NIP-05 paste)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun PairPartnerPage(
    input: String, onInput: (String) -> Unit,
    error: String?, busy: Boolean,
    partnerLinked: Boolean,
    signer: com.turkbot.babytracker.nostr.crypto.NostrSigner?,
    onScanQr: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onLink: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PageHeader(stringResource(R.string.onboarding_pair_title), stringResource(R.string.onboarding_pair_subtitle))
        Spacer(Modifier.height(16.dp))

        if (partnerLinked) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Done, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.onboarding_partner_linked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(24.dp))
            NavButtons(
                onBack = onBack,
                onNext = onSkip,
                nextEnabled = true,
                nextLabel = stringResource(R.string.onboarding_continue)
            )
            return@Column
        }

        // ── Show your QR for the other parent to scan ──
        if (signer != null) {
            val identity = signer.let { s ->
                // Use npub as the QR content — NIP-05 isn't always set during onboarding
                s.npub
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.onboarding_your_qr),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    com.turkbot.babytracker.ui.components.QrCodeDisplay(
                        content = identity,
                        caption = stringResource(R.string.onboarding_qr_caption)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Scan their QR ──
        Button(
            onClick = onScanQr,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_scan_qr))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_or_manual),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            label = { Text(stringResource(R.string.onboarding_npub_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_partner_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.onboarding_back))
            }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip_for_now)) }
            Button(
                onClick = onLink,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.onboarding_link))
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Page 7 — Done
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun DonePage(
    syncMode: SyncMode,
    signerActive: Boolean,
    partnerLinked: Boolean,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Done,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_all_set), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.onboarding_quick_tips), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                HorizontalDivider()
                TipRow(Icons.Filled.Restaurant, stringResource(R.string.onboarding_tip_feed))
                TipRow(Icons.Filled.BabyChangingStation, stringResource(R.string.onboarding_tip_home))
                TipRow(Icons.Filled.People, if (partnerLinked)
                    stringResource(R.string.onboarding_tip_notes_linked)
                else
                    stringResource(R.string.onboarding_tip_notes))
                TipRow(Icons.Filled.Done, if (syncMode == SyncMode.OFFLINE)
                    stringResource(R.string.onboarding_tip_sync_later)
                else if (signerActive)
                    stringResource(R.string.onboarding_tip_syncs)
                else
                    stringResource(R.string.onboarding_tip_backup_key))
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_start_using), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TipRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Shared components
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NavButtons(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    nextLabel: String,
    onSkip: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.onboarding_back))
        }
        if (onSkip != null) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip)) }
        }
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.weight(1f)
        ) {
            Text(nextLabel)
        }
    }
}
