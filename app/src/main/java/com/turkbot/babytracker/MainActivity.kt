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

package com.turkbot.babytracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.turkbot.babytracker.ui.theme.BabyTrackerTheme
import com.turkbot.babytracker.ui.BabyTrackerNavigation
import com.turkbot.babytracker.nostr.amber.AmberBridge

class MainActivity : ComponentActivity() {

    private lateinit var amberLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register the Amber (NIP-55) intent launcher.
        // AmberSigner calls AmberBridge.launch(intent) which routes through this launcher.
        amberLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            AmberBridge.handleResult(result)
        }

        enableEdgeToEdge()
        setContent {
            BabyTrackerTheme {
                // Bind the AmberBridge so AmberSigner can launch intents via the Activity.
                // The bridge is a thin channel: AmberSigner.launch(intent) → amberLauncher.launch
                DisposableEffect(Unit) {
                    AmberBridge.bind { intent -> amberLauncher.launch(intent) }
                    onDispose { AmberBridge.unbind() }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    BabyTrackerNavigation(application as BabyTrackerApp)
                }
            }
        }
    }
}
