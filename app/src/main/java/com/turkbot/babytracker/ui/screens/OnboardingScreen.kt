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
import androidx.compose.ui.platform.LocalContext
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

private enum class SyncMode(val label: String, val icon: ImageVector, val desc: String) {
    OFFLINE(
        "Just This Phone",
        Icons.Filled.CloudOff,
        "Data stays on this phone. No accounts, no internet. You can enable backup later."
    ),
    BACKUP(
        "Auto-Backup",
        Icons.Filled.Key,
        "Your data is encrypted and backed up automatically. Restore on a new phone with the same key."
    ),
    PAIR(
        "Sync With Partner",
        Icons.Filled.People,
        "Everything in Auto-Backup, plus automatic sync with the other parent's phone."
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
                                            keyError = "Could not create a key — try again or skip"
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
                                            keyError = "Amber login failed — try again or skip"
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
                                            partnerError = "Enter an npub, NIP-05, or scan their QR"
                                            partnerBusy = false
                                            return@launch
                                        }
                                        val success = viewModel.setPartnerIdentifier(input)
                                        partnerBusy = false
                                        if (success) {
                                            page = OnboardPage.DONE
                                        } else {
                                            partnerError = "Could not resolve — check the identifier"
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
                        partnerError = "Could not resolve scanned QR — check with partner"
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
            Text("Skip all", style = MaterialTheme.typography.labelMedium)
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
            "Track your baby's feedings, sleep, diapers, weight, and more.",
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
                PrivacyRow(Icons.Filled.Lock, "Your data stays on your phone")
                PrivacyRow(Icons.Filled.CloudOff, "No accounts, no tracking, no cloud")
                PrivacyRow(Icons.Filled.Done, "Works offline — sync is optional")
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
            Text("Get Started", fontWeight = FontWeight.Medium)
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
        PageHeader("Add Your Child", "Let's start with who we're tracking.")
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("Child's name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = dob,
            onValueChange = onDob,
            label = { Text("Date of Birth (YYYY-MM-DD)") },
            placeholder = { Text("Optional") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text("Gender (optional)", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = gender == "boy",
                onClick = { onGender(if (gender == "boy") "" else "boy") },
                label = { Text("Boy") }
            )
            FilterChip(
                selected = gender == "girl",
                onClick = { onGender(if (gender == "girl") "" else "girl") },
                label = { Text("Girl") }
            )
        }

        Spacer(Modifier.height(32.dp))
        NavButtons(
            onBack = onBack,
            onNext = onNext,
            nextEnabled = name.isNotBlank(),
            nextLabel = "Next",
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
        PageHeader("Measurement Units", "Choose how amounts and weights are displayed.")
        Spacer(Modifier.height(16.dp))

        val systems = listOf(
            UnitPreferences.System.METRIC to "Metric (ml, kg, cm)",
            UnitPreferences.System.IMPERIAL to "Imperial (fl oz, lb, in)"
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            systems.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = unitSystem == value,
                    onClick = { onSystem(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, systems.size)
                ) { Text(if (value == UnitPreferences.System.METRIC) "Metric" else "Imperial") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (unitSystem == UnitPreferences.System.METRIC)
                "Bottle amounts in ml, weight in kg, height in cm."
            else
                "Bottle amounts in fl oz, weight in lb, height in inches.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))
        NavButtons(onBack = onBack, onNext = onNext, nextEnabled = true, nextLabel = "Next", onSkip = onSkip)
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
        PageHeader("Sync Settings", "You can change this later in Settings.")
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
        NavButtons(onBack = onBack, onNext = onNext, nextEnabled = true, nextLabel = "Next", onSkip = onSkip)
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
                    mode.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (selected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    mode.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Done,
                    contentDescription = "Selected",
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
        PageHeader("Create Your Backup Key", "This encrypts your data so only you can access it.")
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
                    Text("Your key is ready.",
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
                                "Keep Your Key Safe",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            "This key is the only way to access your data. If you lose it, " +
                                "your backup is gone forever — there is no password reset. " +
                                "Write it down somewhere safe, or back up your phone regularly.\n\n" +
                                "You can view and copy your key later in Settings → Nostr Identity.",
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
                nextLabel = "Continue"
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
                        "Create a New Key",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "One tap. We'll generate a secure key for you automatically.",
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
            Text(if (hasAmber) "Use Amber App Instead" else "Use Amber (Install Required)")
        }
        if (!hasAmber) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Amber keeps your key in a separate app. Install from zapstore.dev/apps/com.greenart7c3.amber",
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
                Text("Back")
            }
            TextButton(onClick = onSkip) { Text("Skip for now") }
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
        PageHeader("Link With The Other Parent", "Scan their QR code, or enter their npub or NIP-05.")
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
                    Text("Partner linked successfully.",
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
                nextLabel = "Continue"
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
                        "Your QR Code",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    com.turkbot.babytracker.ui.components.QrCodeDisplay(
                        content = identity,
                        caption = "Have the other parent scan this in their Infans app."
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
            Text("Scan Partner's QR")
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Or enter manually:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            label = { Text("npub or NIP-05") },
            placeholder = { Text("npub1... or name@domain.com") },
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
                Text("Back")
            }
            TextButton(onClick = onSkip) { Text("Skip for now") }
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
                    Text("Link")
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
        Text("You're all set!", style = MaterialTheme.typography.headlineMedium,
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
                Text("Quick Tips", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                HorizontalDivider()
                TipRow(Icons.Filled.Restaurant, "Use the Feed tab to log bottle, breast, or solid feedings.")
                TipRow(Icons.Filled.BabyChangingStation, "Diaper, pumping, health, and vaccines are on the Home screen.")
                TipRow(Icons.Filled.People, if (partnerLinked)
                    "Notes you leave will appear on the other parent's phone."
                else
                    "Notes are a quick way to remember things about your baby.")
                TipRow(Icons.Filled.Done, if (syncMode == SyncMode.OFFLINE)
                    "To enable backup or partner sync later, go to Settings."
                else if (signerActive)
                    "Data syncs automatically after each entry."
                else
                    "Set up your backup key in Settings to start syncing.")
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Start Using Infans", fontWeight = FontWeight.Medium)
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
            Text("Back")
        }
        if (onSkip != null) {
            TextButton(onClick = onSkip) { Text("Skip") }
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
