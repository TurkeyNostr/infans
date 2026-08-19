package com.turkbot.babytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.model.Milestone
import com.turkbot.babytracker.ui.viewmodel.MilestonesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MilestonesScreen(viewModel: MilestonesViewModel) {
    val milestones by viewModel.milestones.collectAsState()
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Milestone title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (title.isNotBlank()) {
                    viewModel.addMilestone(title.trim(), note.trim().ifBlank { null })
                    title = ""
                    note = ""
                }
            },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Record Milestone")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (milestones.isEmpty()) {
            Text(
                text = "No milestones recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(milestones, key = { it.id }) { milestone ->
                    MilestoneCard(
                        milestone = milestone,
                        onDelete = { viewModel.deleteMilestone(milestone.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MilestoneCard(
    milestone: Milestone,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d yyyy", Locale.getDefault()) }
    val formattedDate = remember(milestone.date) {
        dateFormat.format(Date(milestone.date))
    }

    Card(
        colors = CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "\uD83C\uDFC6",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = milestone.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!milestone.note.isNullOrBlank()) {
                    Text(
                        text = milestone.note,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete milestone"
                )
            }
        }
    }
}
