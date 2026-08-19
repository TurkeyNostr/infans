package com.turkbot.babytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.turkbot.babytracker.ui.theme.BabyTrackerTheme
import com.turkbot.babytracker.ui.BabyTrackerNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BabyTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BabyTrackerNavigation(application as BabyTrackerApp)
                }
            }
        }
    }
}
