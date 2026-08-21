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
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
    WELCOME, ADD_CHILD, UNITS, CHOOSE_MODE, SETUP_NOSTR, SETUP_PARTNER, DONE
}

private enum class SyncMode(val label: String, val icon: ImageVector, val desc: String) {
    OFFLINE(
        "Offline Only",
        Icons.Filled.CloudOff,
        "Data stays on this phone. No accounts, no internet. Use Settings to enable backup later."
    ),
    RELAY_SOLO(
        "Relay Backup",
        Icons.Filled.CloudUpload,
        "Encrypted backups to Nostr relays. Restore on a new phone by logging in with the same key."
    ),
    PARTNER(
        "Partner Sync",
        Icons.Filled.People,
        "Everything in Relay Backup, plus automatic sync with the other parent's phone."
    )
}

private enum class NostrChoice { GENERATE, IMPORT, AMBER, SKIP }

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
    var nostrChoice by remember { mutableStateOf(NostrChoice.GENERATE) }

    // ── Add-child form state ──
    var childName by rememberSaveable { mutableStateOf("") }
    var childDob by rememberSaveable { mutableStateOf("") }
    var childGender by rememberSaveable { mutableStateOf("") }

    // ── Nostr setup state ──
    var nsecInput by rememberSaveable { mutableStateOf("") }
    var nostrError by rememberSaveable { mutableStateOf<String?>(null) }
    var nostrBusy by remember { mutableStateOf(false) }
    val signer by nostrManager.signer.collectAsState()
    val amberInstalled by remember { mutableStateOf(viewModel.isAmberInstalled()) }

    // ── Partner setup state ──
    var partnerInput by rememberSaveable { mutableStateOf("") }
    var partnerError by rememberSaveable { mutableStateOf<String?>(null) }
    var partnerBusy by remember { mutableStateOf(false) }
    val partnerNpub by viewModel.partnerNpub.collectAsState()

    // ── Units state ──
    var unitSystem by remember { mutableStateOf(UnitPreferences.getSystem(context)) }

    Scaffold { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── "Skip all" — visible on every page, top-right ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        setOnboardingComplete(context)
                        onComplete()
                    }) {
                        Text("Skip all", style = MaterialTheme.typography.labelMedium)
                    }
                }
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
                                onNext = { page = OnboardPage.CHOOSE_MODE },
                                onSkip = { page = OnboardPage.CHOOSE_MODE }
                            )
                        }
                        OnboardPage.CHOOSE_MODE -> item {
                            ChooseModePage(
                                selected = syncMode,
                                onSelect = { syncMode = it },
                                onBack = { page = OnboardPage.UNITS },
                                onNext = {
                                    page = when (syncMode) {
                                        SyncMode.OFFLINE -> OnboardPage.DONE
                                        SyncMode.RELAY_SOLO,
                                        SyncMode.PARTNER -> OnboardPage.SETUP_NOSTR
                                    }
                                },
                                onSkip = {
                                    page = when (syncMode) {
                                        SyncMode.OFFLINE -> OnboardPage.DONE
                                        SyncMode.RELAY_SOLO,
                                        SyncMode.PARTNER -> OnboardPage.SETUP_NOSTR
                                    }
                                }
                            )
                        }
                        OnboardPage.SETUP_NOSTR -> item {
                            SetupNostrPage(
                                choice = nostrChoice,
                                onChoice = { nostrChoice = it },
                                nsecInput = nsecInput,
                                onNsec = { nsecInput = it; nostrError = null },
                                error = nostrError,
                                busy = nostrBusy,
                                amberInstalled = amberInstalled,
                                signerActive = signer != null,
                                onBack = { page = OnboardPage.CHOOSE_MODE },
                                onSkip = { page = OnboardPage.DONE },
                                onSetup = {
                                    scope.launch {
                                        nostrBusy = true
                                        nostrError = null
                                        val ok = when (nostrChoice) {
                                            NostrChoice.GENERATE -> {
                                                viewModel.generateNostrIdentity()
                                                // generateIdentity is fire-and-forget; check signer
                                                kotlinx.coroutines.delay(500)
                                                signer != null || nostrManager.signer.value != null
                                            }
                                            NostrChoice.IMPORT -> {
                                                val result = nostrManager.importIdentity(nsecInput.trim())
                                                result != null
                                            }
                                            NostrChoice.AMBER -> {
                                                val result = nostrManager.loginWithAmber()
                                                result != null
                                            }
                                            NostrChoice.SKIP -> true
                                        }
                                        nostrBusy = false
                                        if (ok) {
                                            page = if (syncMode == SyncMode.PARTNER)
                                                OnboardPage.SETUP_PARTNER else OnboardPage.DONE
                                        } else {
                                            nostrError = "Setup failed — try again or skip for now"
                                        }
                                    }
                                }
                            )
                        }
                        OnboardPage.SETUP_PARTNER -> item {
                            SetupPartnerPage(
                                input = partnerInput,
                                onInput = { partnerInput = it; partnerError = null },
                                error = partnerError,
                                busy = partnerBusy,
                                partnerLinked = partnerNpub != null,
                                onBack = { page = OnboardPage.SETUP_NOSTR },
                                onSkip = { page = OnboardPage.DONE },
                                onLink = {
                                    scope.launch {
                                        partnerBusy = true
                                        partnerError = null
                                        val input = partnerInput.trim()
                                        if (input.isEmpty()) {
                                            partnerError = "Enter an npub or NIP-05"
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
                PrivacyRow(Icons.Filled.Key, "Your data stays on your phone")
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
// Page 4 — Choose Sync Mode
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChooseModePage(
    selected: SyncMode,
    onSelect: (SyncMode) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PageHeader("How Do You Want To Sync?", "You can change this later in Settings.")
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
// Page 5 — Set up Nostr identity (only for relay/partner modes)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetupNostrPage(
    choice: NostrChoice,
    onChoice: (NostrChoice) -> Unit,
    nsecInput: String,
    onNsec: (String) -> Unit,
    error: String?,
    busy: Boolean,
    amberInstalled: Boolean,
    signerActive: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onSetup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PageHeader("Create Your Nostr Identity", "This encrypts and identifies your backups on Nostr relays.")
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
                    Text("Identity is set up and ready.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(24.dp))
            NavButtons(
                onBack = onBack,
                onNext = onSkip,  // proceed to next page
                nextEnabled = true,
                nextLabel = "Continue"
            )
            return@Column
        }

        NostrOptionCard(
            title = "Generate New Key",
            desc = "Creates a fresh Nostr key. Simplest option.",
            icon = Icons.Filled.Key,
            selected = choice == NostrChoice.GENERATE,
            onClick = { onChoice(NostrChoice.GENERATE) }
        )
        Spacer(Modifier.height(8.dp))

        NostrOptionCard(
            title = "Import nsec",
            desc = "Paste an existing Nostr private key (nsec1...).",
            icon = Icons.Filled.Key,
            selected = choice == NostrChoice.IMPORT,
            onClick = { onChoice(NostrChoice.IMPORT) }
        )
        if (choice == NostrChoice.IMPORT) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = nsecInput,
                onValueChange = onNsec,
                label = { Text("nsec1...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))
        NostrOptionCard(
            title = if (amberInstalled) "Log in with Amber" else "Log in with Amber (install)",
            desc = "Your private key stays in the Amber app. Infans never sees it.",
            icon = Icons.Filled.AccountCircle,
            selected = choice == NostrChoice.AMBER,
            onClick = { onChoice(NostrChoice.AMBER) }
        )
        if (choice == NostrChoice.AMBER && !amberInstalled) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Install Amber from zapstore.dev/apps/com.greenart7c3.amber",
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
            Button(
                onClick = onSetup,
                enabled = !busy && choice != NostrChoice.SKIP,
                modifier = Modifier.weight(1f)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                    )
                } else {
                    Text("Set Up")
                }
            }
        }
    }
}

@Composable
private fun NostrOptionCard(
    title: String, desc: String, icon: ImageVector,
    selected: Boolean, onClick: () -> Unit
) {
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon, contentDescription = null, modifier = Modifier.size(24.dp),
                tint = if (selected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (selected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface)
                Text(desc, style = MaterialTheme.typography.bodySmall,
                    color = if (selected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Page 6 — Link Partner (only for partner mode)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetupPartnerPage(
    input: String, onInput: (String) -> Unit,
    error: String?, busy: Boolean,
    partnerLinked: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onLink: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PageHeader("Link With The Other Parent", "Enter their npub or NIP-05 so data syncs between your phones.")
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

        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            label = { Text("Partner's npub or NIP-05") },
            placeholder = { Text("npub1... or name@domain.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask the other parent for their npub or NIP-05. They can find it in Infans under Settings → Nostr Identity.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("Link Partner")
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
                TipRow(Icons.Filled.BabyChangingStation, "Diaper, pumping, and health are on the Home screen.")
                TipRow(Icons.Filled.People, if (partnerLinked)
                    "Notes you leave will appear on the other parent's phone."
                else
                    "Notes are a quick way to remember things about your baby.")
                TipRow(Icons.Filled.Done, if (syncMode == SyncMode.OFFLINE)
                    "To enable backup or partner sync later, go to Settings."
                else if (signerActive)
                    "Data syncs automatically after each entry."
                else
                    "Set up your Nostr identity in Settings to start syncing.")
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
