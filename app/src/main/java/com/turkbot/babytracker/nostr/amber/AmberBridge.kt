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

package com.turkbot.babytracker.nostr.amber

import android.content.Intent
import androidx.activity.result.ActivityResult
import kotlinx.coroutines.channels.Channel

/**
 * Bridge between the Activity's ActivityResultLauncher and suspend functions.
 *
 * MainActivity registers a launcher via rememberLauncherForActivityResult and binds
 * it here. AmberSigner calls [launch] (suspend) which sends the intent through the
 * launcher and awaits the result channel.
 *
 * This must be bound before any Amber operation is attempted.
 */
object AmberBridge {

    private var launcherRef: ((Intent) -> Unit)? = null
    private var resultChannel: Channel<ActivityResult>? = null

    /**
     * Called from MainActivity's DisposableEffect.
     * @param launchFn a function that launches the given Intent via the registered launcher.
     *                 Captured as a lambda so we don't hold a Compose-specific type.
     */
    fun bind(launchFn: (Intent) -> Unit) {
        launcherRef = launchFn
    }

    fun unbind() {
        launcherRef = null
        resultChannel?.close()
        resultChannel = null
    }

    fun isBound(): Boolean = launcherRef != null

    /**
     * Launch an Intent and suspend until the result returns.
     * Must be called from a coroutine. The result is delivered via [handleResult].
     */
    suspend fun launch(intent: Intent): ActivityResult {
        val launchFn = launcherRef ?: throw IllegalStateException(
            "AmberBridge is not bound. Call AmberBridge.bind() from MainActivity first."
        )
        val channel = Channel<ActivityResult>(1).also { resultChannel = it }
        launchFn(intent)
        return channel.receive()
    }

    /**
     * Called from the ActivityResultLauncher callback in MainActivity.
     * Feeds the result to the suspended [launch] caller.
     */
    fun handleResult(result: ActivityResult) {
        resultChannel?.trySend(result)
        resultChannel = null
    }
}
