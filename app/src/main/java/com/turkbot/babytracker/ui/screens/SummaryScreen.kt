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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.turkbot.babytracker.R
import com.turkbot.babytracker.data.entities.*
import com.turkbot.babytracker.ui.components.BarChart
import com.turkbot.babytracker.ui.components.EditTimestampDialog
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.HapticController
import com.turkbot.babytracker.util.Units
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SummaryScreen(
    viewModel: BabyViewModel,
    onNavigateToWeight: () -> Unit = {},
    onNavigateToCharts: () -> Unit = {},
    onNavigateToMilestones: () -> Unit = {},
    onNavigateToDiaper: () -> Unit = {},
    onNavigateToPumping: () -> Unit = {},
    onNavigateToHealth: () -> Unit = {},
    onNavigateToBath: () -> Unit = {},
    onNavigateToSleep: () -> Unit = {},
    onNavigateToFeed: () -> Unit = {}
) {
    val feedings by viewModel.feedings.collectAsState()
    val sleeps by viewModel.sleeps.collectAsState()
    val weights by viewModel.weights.collectAsState()
    val milestones by viewModel.milestones.collectAsState()
    val diapers by viewModel.diapers.collectAsState()
    val pumpings by viewModel.pumpings.collectAsState()
    val healthRecords by viewModel.healthRecords.collectAsState()
    val child by viewModel.activeChild.collectAsState()
    val children by viewModel.children.collectAsState()
    var editingFeeding by remember { mutableStateOf<Feeding?>(null) }
    var editingSleep by remember { mutableStateOf<Sleep?>(null) }

    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    val todayFeedings = feedings.filter { f ->
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(f.time)) == today
    }
    val todaySleeps = sleeps.filter { s ->
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(s.start)) == today
    }

    // Last activity timestamps for "last fed" / "last slept" / "last diaper"
    val lastFeeding = feedings.maxByOrNull { it.time }
    val lastSleep = sleeps.maxByOrNull { it.start }
    val lastDiaper = diapers.maxByOrNull { it.time }
    val context = androidx.compose.ui.platform.LocalContext.current

    // ── Phase A: quick-log smart defaults ──
    val lastBottle = feedings.filter { it.type == "bottle" }.maxByOrNull { it.time }
    val lastPumping = pumpings.maxByOrNull { it.time }

    // ── Phase A: live session card ──
    // Reads the same persisted timer state LiveTimer writes (start_<label>),
    // so the card survives process death exactly like the timers themselves.
    // Stop/end deliberately NOT duplicated here — the card deep-links to the
    // Feed/Sleep tab where LiveTimer owns the stop logic (partner sync,
    // alarm cancel). Single source of truth for session-ending side effects.
    val timerPrefs = remember {
        context.getSharedPreferences("live_timer_prefs", Context.MODE_PRIVATE)
    }
    val remoteSleepSession by viewModel.remoteSleepSession.collectAsState()
    val remoteBreastSession by viewModel.remoteBreastSession.collectAsState()
    val rSleep = remoteSleepSession  // local vals — delegated props can't smart-cast
    val rBreast = remoteBreastSession
    val localSleepStart = timerPrefs.getLong("start_Sleep", 0L)
    val localBreastStart = timerPrefs.getLong("start_Breast", 0L)
    // Remote session wins if both exist (same precedence LiveTimer uses).
    val activeSession: Pair<String, Long> = when {
        rSleep != null -> "sleep" to rSleep.startTime
        rBreast != null -> "breast" to rBreast.startTime
        localSleepStart > 0L -> "sleep" to localSleepStart
        localBreastStart > 0L -> "breast" to localBreastStart
        else -> "" to 0L
    }
    val (sessionKind, sessionStart) = activeSession
    // Tick once per second only while a session is actually running.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sessionKind) {
        if (sessionKind.isNotEmpty()) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    // Undo support for quick-log: snackbar with an Undo action deletes the
    // exact record that was just created (ids come back from the ViewModel).
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.summary_snack_undo)
    suspend fun showUndo(message: String, onDelete: () -> Unit) {
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            withDismissAction = false
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onDelete()
    }

    val todayDiapers = diapers.filter { d ->
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(d.time)) == today
    }

    // Elapsed time since last event
    fun timeAgo(time: Long): String {
        val mins = ((System.currentTimeMillis() - time) / 60000).toInt()
        return when {
            mins < 1 -> context.getString(R.string.summary_just_now)
            mins < 60 -> context.getString(R.string.summary_minutes_ago, mins)
            mins < 1440 -> context.getString(R.string.summary_hours_minutes_ago, mins / 60, mins % 60)
            else -> context.getString(R.string.summary_days_ago, mins / 1440)
        }
    }

    // 7-day trend data
    val days = (0..6).map { i ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -i)
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }.reversed()

    val dayLabels = days.map { d ->
        val cal = Calendar.getInstance()
        cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(d)!!
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "S"
            Calendar.MONDAY -> "M"
            Calendar.TUESDAY -> "T"
            Calendar.WEDNESDAY -> "W"
            Calendar.THURSDAY -> "T"
            Calendar.FRIDAY -> "F"
            Calendar.SATURDAY -> "S"
            else -> "?"
        }
    }

    val feedCounts = days.map { day ->
        // Deduplicate breast-feeding records: both parents can log the same
        // session.  Group by (type, time bucket, childId) and count groups.
        feedings.filter { f ->
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(f.time)) == day
        }
        .groupBy { Triple(it.type, it.time / 600_000L, it.childId) }
        .size
    }
    val sleepHours = days.map { day ->
        val daySleeps = sleeps.filter { s ->
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(s.start)) == day
        }
        // Deduplicate: both parents can log the same session with different
        // UUIDs.  Group by (duration, start rounded to 10-min bucket, childId)
        // and keep one per group to prevent double-counting.
        daySleeps
            .groupBy { Triple(it.duration, it.start / 600_000L, it.childId) }
            .values
            .sumOf { group -> group.first().duration } / 60.0
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Hero child info card — gradient fill ──────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        child?.name ?: stringResource(R.string.summary_no_child_selected),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    if (child?.dob != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.summary_age, Units.ageFromDOB(child!!.dob)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    // Multi-child selector — show dropdown if more than 1 child
                    if (children.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        var showChildMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(
                                onClick = { showChildMenu = true },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    stringResource(R.string.summary_switch_child),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            DropdownMenu(
                                expanded = showChildMenu,
                                onDismissRequest = { showChildMenu = false }
                            ) {
                                children.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.name) },
                                        onClick = {
                                            viewModel.selectChild(c.id)
                                            showChildMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Phase A: live session card — the running timer, front and center ──
        if (sessionKind.isNotEmpty()) {
            item {
                LiveSessionCard(
                    kind = sessionKind,
                    startTime = sessionStart,
                    nowMs = nowMs,
                    isRemote = (sessionKind == "sleep" && rSleep != null) ||
                            (sessionKind == "breast" && rBreast != null),
                    onOpen = { if (sessionKind == "sleep") onNavigateToSleep() else onNavigateToFeed() }
                )
            }
        }

        // ── Phase A: status strip — state before history ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusTile(
                    icon = Icons.Default.Restaurant,
                    label = stringResource(R.string.summary_last_feeding),
                    value = lastFeeding?.let { timeAgo(it.time) } ?: "—",
                    sub = lastFeeding?.let {
                        if (it.type == "bottle" && it.amount != null && it.unit != null)
                            Units.fmtAmount(it.amount, it.unit)
                        else Units.feedTypeLabel(it.type)
                    } ?: "",
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.primary
                )
                StatusTile(
                    icon = Icons.Default.Spa,
                    label = stringResource(R.string.summary_last_diaper),
                    value = lastDiaper?.let { timeAgo(it.time) } ?: "—",
                    sub = lastDiaper?.let {
                        it.contents.replaceFirstChar { c -> c.uppercase() }
                    } ?: "",
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.secondary
                )
                StatusTile(
                    icon = Icons.Default.Bedtime,
                    label = stringResource(R.string.summary_sleep),
                    value = Units.fmtDuration(
                        todaySleeps
                            .groupBy { Triple(it.duration, it.start / 600_000L, it.childId) }
                            .values
                            .sumOf { group -> group.first().duration }
                    ),
                    sub = stringResource(R.string.summary_today),
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // ── (Old "Last activity" card removed — superseded by the status strip) ──

        // ── Today's totals ─────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.summary_todays_totals),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    // Total bottle intake (ml)
                    val totalMl = todayFeedings
                        .filter { it.type == "bottle" && it.amount != null && it.unit != null }
                        .sumOf { f -> Units.amountToMl(f.amount!!, f.unit!!) }
                    val totalBreastMin = todayFeedings
                        .filter { it.type == "breast" }
                        .sumOf { it.duration ?: 0 }
                    val totalSleepMin = todaySleeps
                        .groupBy { Triple(it.duration, it.start / 600_000L, it.childId) }
                        .values
                        .sumOf { group -> group.first().duration }
                    val totalDiapers = todayDiapers.size

                    TotalsRow(
                        icon = Icons.Default.Restaurant,
                        label = stringResource(R.string.summary_feedings),
                        value = if (todayFeedings.isEmpty()) "—" else {
                            val parts = mutableListOf<String>()
                            if (totalMl > 0) parts.add(stringResource(R.string.summary_total_ml, totalMl.toInt()))
                            if (totalBreastMin > 0) parts.add(stringResource(R.string.summary_total_breast_min, totalBreastMin))
                            if (parts.isEmpty()) "${todayFeedings.size} session${if (todayFeedings.size > 1) "s" else ""}"
                            else parts.joinToString(" · ")
                        },
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                    TotalsRow(
                        icon = Icons.Default.Bedtime,
                        label = stringResource(R.string.summary_sleep),
                        value = if (totalSleepMin > 0) Units.fmtDuration(totalSleepMin) else "—",
                        iconTint = MaterialTheme.colorScheme.tertiary
                    )
                    TotalsRow(
                        icon = Icons.Default.Spa,
                        label = stringResource(R.string.summary_diapers),
                        value = if (totalDiapers > 0) "$totalDiapers" else "—",
                        iconTint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // ── Phase A: quick log — one tap, smart defaults, undo ──
        item {
            Column {
                Text(
                    stringResource(R.string.summary_quick_log),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.summary_quick_log_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickLogTile(
                        icon = Icons.Default.LocalDrink,
                        label = stringResource(R.string.summary_tile_bottle),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // Smart default: last bottle's amount/unit, else 120 ml
                            val amount = lastBottle?.amount ?: 120.0
                            val unit = lastBottle?.unit ?: "ml"
                            val id = viewModel.addFeeding(
                                type = "bottle", amount = amount, unit = unit,
                                breastSide = null, duration = null, note = null
                            )
                            if (id != null) {
                                HapticController.click(context)
                                scope.launch {
                                    showUndo(
                                        context.getString(R.string.summary_snack_bottle, Units.fmtAmount(amount, unit))
                                    ) { viewModel.deleteFeeding(id) }
                                }
                            }
                        }
                    )
                    QuickLogTile(
                        icon = Icons.Default.ChildCare,
                        label = stringResource(R.string.feed_type_breast),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // Nursing needs the live timer (duration unknown) —
                            // the Feed tab owns it; never write a 0-min record.
                            onNavigateToFeed()
                        }
                    )
                    QuickLogTile(
                        icon = Icons.Default.BabyChangingStation,
                        label = stringResource(R.string.summary_diaper),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val id = viewModel.addDiaper(contents = "wet", color = null, note = null)
                            if (id.isNotEmpty()) {
                                HapticController.click(context)
                                scope.launch {
                                    showUndo(context.getString(R.string.summary_snack_diaper)) {
                                        viewModel.deleteDiaper(id)
                                    }
                                }
                            }
                        }
                    )
                    QuickLogTile(
                        icon = Icons.Default.Bathtub,
                        label = stringResource(R.string.summary_bath),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val id = viewModel.addBath(type = "full", note = null)
                            if (id.isNotEmpty()) {
                                HapticController.click(context)
                                scope.launch {
                                    showUndo(context.getString(R.string.summary_snack_bath)) {
                                        viewModel.deleteBath(id)
                                    }
                                }
                            }
                        }
                    )
                    QuickLogTile(
                        icon = Icons.Default.WaterDrop,
                        label = stringResource(R.string.summary_pumping),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // Smart default: last pump amount/unit, else 80 ml
                            val amount = lastPumping?.amount ?: 80.0
                            val unit = lastPumping?.unit
                                ?: com.turkbot.babytracker.util.UnitPreferences.defaultPumpUnit(context)
                            val id = viewModel.addPumping(
                                amountMl = amount, unit = unit,
                                duration = null, side = null, note = null
                            )
                            if (id.isNotEmpty()) {
                                HapticController.click(context)
                                scope.launch {
                                    showUndo(
                                        context.getString(R.string.summary_snack_pump, Units.fmtAmount(amount, unit))
                                    ) { viewModel.deletePumping(id) }
                                }
                            }
                        }
                    )
                }
            }
        }

        // ── Quick action chips ─────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Bathtub,
                    label = stringResource(R.string.summary_bath),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToBath
                )
                QuickActionCard(
                    icon = Icons.Default.ShowChart,
                    label = stringResource(R.string.summary_charts),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToCharts
                )
                QuickActionCard(
                    icon = Icons.Default.EmojiEvents,
                    label = stringResource(R.string.summary_milestones),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMilestones
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Spa,
                    label = stringResource(R.string.summary_diaper),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToDiaper
                )
                QuickActionCard(
                    icon = Icons.Default.WaterDrop,
                    label = stringResource(R.string.summary_pumping),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToPumping
                )
                QuickActionCard(
                    icon = Icons.Default.HealthAndSafety,
                    label = stringResource(R.string.summary_health),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToHealth
                )
            }
        }

        // ── Today's stats ──────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Default.Restaurant,
                    label = stringResource(R.string.summary_feedings_today),
                    value = todayFeedings.size.toString(),
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    icon = Icons.Default.Bedtime,
                    label = stringResource(R.string.summary_sleep_today),
                    value = Units.fmtDuration(
                        todaySleeps
                            .groupBy { Triple(it.duration, it.start / 600_000L, it.childId) }
                            .values
                            .sumOf { group -> group.first().duration }
                    ),
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Default.Spa,
                    label = stringResource(R.string.summary_diapers_today),
                    value = todayDiapers.size.toString(),
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.secondary
                )
                StatCard(
                    icon = Icons.Default.WaterDrop,
                    label = stringResource(R.string.summary_pumping_today),
                    value = pumpings.count { p ->
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(p.time)) == today
                    }.toString(),
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // ── Feeding trend ──────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.summary_feeding_trend),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.summary_feedings_per_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    BarChart(
                        values = feedCounts.map { it.toDouble() },
                        labels = dayLabels,
                        barColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ── Sleep trend ────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.summary_sleep_trend),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.summary_hours_per_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    BarChart(
                        values = sleepHours,
                        labels = dayLabels,
                        barColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // ── Today's activity list ──────────────────────────
        item {
            Text(
                stringResource(R.string.summary_todays_activity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (todayFeedings.isEmpty() && todaySleeps.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        stringResource(R.string.summary_no_activity_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
        } else {
            items(todayFeedings) { f ->
                ActivityRow(
                    icon = Icons.Default.Restaurant,
                    title = Units.feedTypeLabel(f.type),
                    subtitle = "${timeFmt.format(Date(f.time))}" +
                        (if (f.amount != null && f.unit != null) " · ${Units.fmtAmount(f.amount, f.unit)}" else ""),
                    onDelete = { viewModel.deleteFeeding(f.id) },
                    onEditTime = { editingFeeding = f },
                    iconTint = MaterialTheme.colorScheme.primary
                )
            }
            items(todaySleeps) { s ->
                val sleepEnd = s.start + s.duration * 60_000L
                ActivityRow(
                    icon = Icons.Default.Bedtime,
                    title = stringResource(R.string.summary_sleep_duration, Units.fmtDuration(s.duration)),
                    subtitle = "${timeFmt.format(Date(s.start))} - ${timeFmt.format(Date(sleepEnd))}",
                    onDelete = { viewModel.deleteSleep(s.id) },
                    onEditTime = { editingSleep = s },
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }

    // ── Edit timestamp dialogs ──────────────────────────
    // SnackbarHost floats over the list at the bottom of the screen.
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 8.dp)
    )
    }
    editingFeeding?.let { feeding ->
        EditTimestampDialog(
            initialEpochMillis = feeding.time,
            onDismiss = { editingFeeding = null },
            onSave = { newTime ->
                viewModel.updateFeedingTime(feeding.id, newTime)
                editingFeeding = null
            }
        )
    }
    editingSleep?.let { sleep ->
        EditTimestampDialog(
            initialEpochMillis = sleep.start,
            onDismiss = { editingSleep = null },
            onSave = { newTime ->
                viewModel.updateSleepStart(sleep.id, newTime)
                editingSleep = null
            }
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Compact status tile for the top-of-screen strip (state before history). */
@Composable
private fun StatusTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** One-tap quick-log tile: icon + label, tap writes with smart defaults. */
@Composable
private fun QuickLogTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1
            )
        }
    }
}

/**
 * Live "Sleeping / Nursing — started HH:mm" card for the Summary. Mirrors the
 * running timer without owning any stop logic: the Open button deep-links to
 * the Feed/Sleep tab where LiveTimer handles stopping (partner sync, alarms).
 *
 * @param kind  "sleep" or "breast"
 * @param nowMs ticking clock from the caller (recomposes once per second)
 */
@Composable
private fun LiveSessionCard(
    kind: String,
    startTime: Long,
    nowMs: Long,
    isRemote: Boolean,
    onOpen: () -> Unit
) {
    val title = if (kind == "sleep") stringResource(R.string.summary_sleeping)
                else stringResource(R.string.summary_nursing)
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val elapsed = (nowMs - startTime).coerceAtLeast(0L)
    val totalSec = elapsed / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val elapsedText = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (kind == "sleep") Icons.Default.Bedtime else Icons.Default.ChildCare,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    stringResource(R.string.summary_started_at, timeFmt.format(Date(startTime))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
                if (isRemote) {
                    Text(
                        stringResource(R.string.timer_started_by_partner),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Text(
                elapsedText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(end = 8.dp)
            )
            TextButton(onClick = onOpen) {
                Text(stringResource(R.string.summary_open_timer))
            }
        }
    }
}

@Composable
private fun TotalsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ActivityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onDelete: () -> Unit,
    onEditTime: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onEditTime != null) {
                IconButton(onClick = onEditTime) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.summary_edit_time), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.summary_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


// ── BarChart moved to ui/components/BarChart.kt ──
