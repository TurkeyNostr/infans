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
import com.turkbot.babytracker.debug.DebugLogger as Dbg
import com.turkbot.babytracker.debug.DebugLogger.Category as Cat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Bridge between the Activity's ActivityResultLauncher and suspend functions.
 *
 * MainActivity registers a launcher via rememberLauncherForActivityResult and binds
 * it here. AmberSigner calls [launch] (suspend) which sends the intent through the
 * launcher and awaits the result channel.
 *
 * This must be bound before any Amber operation is attempted.
 *
 * CRITICAL: All Amber operations are serialized via a mutex. Only one intent is
 * in-flight at a time — subsequent callers queue and wait. This prevents the
 * prompt storm where 100 historical DMs each fire two decrypt intents at Amber
 * simultaneously, overwhelming the signer and crashing the app.
 */
object AmberBridge {

    private var launcherRef: ((Intent) -> Unit)? = null
    @Volatile
    private var currentChannel: Channel<ActivityResult>? = null
    private val mutex = Mutex()

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
        currentChannel?.close()
        currentChannel = null
        Dbg.info(Cat.AMBER, "AmberBridge unbound — Activity destroyed")
    }

    fun isBound(): Boolean = launcherRef != null

    /**
     * Launch an Intent and suspend until the result returns.
     * Must be called from a coroutine. The result is delivered via [handleResult].
     *
     * Serialized: only one Amber intent is in-flight at a time. If another call
     * is already waiting for a result, this call blocks on the mutex until that
     * result returns, then proceeds.
     */
    suspend fun launch(intent: Intent): ActivityResult = mutex.withLock {
        val launchFn = launcherRef ?: throw IllegalStateException(
            "AmberBridge is not bound. Call AmberBridge.bind() from MainActivity first."
        )
        val channel = Channel<ActivityResult>(1).also { currentChannel = it }
        // ActivityResultLauncher.launch() must be called on the main thread.
        // Relay event handlers run on Dispatchers.IO, so we hop to Main here.
        withContext(Dispatchers.Main) {
            launchFn(intent)
        }
        val result = channel.receive()
        currentChannel = null
        result
    }

    /**
     * Called from the ActivityResultLauncher callback in MainActivity.
     * Feeds the result to the suspended [launch] caller.
     */
    fun handleResult(result: ActivityResult) {
        currentChannel?.trySend(result)
        currentChannel = null
    }
}
