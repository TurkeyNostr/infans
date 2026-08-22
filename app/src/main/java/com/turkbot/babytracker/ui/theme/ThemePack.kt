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
package com.turkbot.babytracker.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── Color schemes for each theme pack ──

private val PurpleLight = lightColorScheme(
    primary = Color(0xFF6750A4), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4), onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFEF7FF), onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF), onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC), onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7), surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9)
)

private val PurpleDark = darkColorScheme(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48), onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141218), onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218), onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFF0F0D13), surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26), surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B)
)

private val BlueLight = lightColorScheme(
    primary = Color(0xFF1565C0), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF), onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F7), onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF), onTertiaryContainer = Color(0xFF241532),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFDFCFF), onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFF), onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB), onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C7CF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFEBEEF3), surfaceContainerHigh = Color(0xFFE6E9EE),
    surfaceContainerHighest = Color(0xFFE0E3E8)
)

private val BlueDark = darkColorScheme(
    primary = Color(0xFF9ECAFF), onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D), onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB), onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4856), onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD7BEE4), onTertiary = Color(0xFF3A2948),
    tertiaryContainer = Color(0xFF52405F), onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111418), onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF111418), onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF42474E), onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199), outlineVariant = Color(0xFF42474E),
    surfaceContainerLowest = Color(0xFF0C0E12), surfaceContainerLow = Color(0xFF1A1C1E),
    surfaceContainer = Color(0xFF1E2024), surfaceContainerHigh = Color(0xFF282A2E),
    surfaceContainerHighest = Color(0xFF333539)
)

private val GreenLight = lightColorScheme(
    primary = Color(0xFF386A20), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F397), onPrimaryContainer = Color(0xFF042100),
    secondary = Color(0xFF55624C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E7CB), onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF386666), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBEBEB), onTertiaryContainer = Color(0xFF002020),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FBF2), onBackground = Color(0xFF191D16),
    surface = Color(0xFFF8FBF2), onSurface = Color(0xFF191D16),
    surfaceVariant = Color(0xFFE0E4D6), onSurfaceVariant = Color(0xFF43483D),
    outline = Color(0xFF73796E), outlineVariant = Color(0xFFC3C8BB),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF2F5EC),
    surfaceContainer = Color(0xFFEDEFE6), surfaceContainerHigh = Color(0xFFE8E9E1),
    surfaceContainerHighest = Color(0xFFE2E4DB)
)

private val GreenDark = darkColorScheme(
    primary = Color(0xFF9DD67E), onPrimary = Color(0xFF0F3900),
    primaryContainer = Color(0xFF1F5108), onPrimaryContainer = Color(0xFFB8F397),
    secondary = Color(0xFFBCCBAD), onSecondary = Color(0xFF273420),
    secondaryContainer = Color(0xFF3D4A35), onSecondaryContainer = Color(0xFFD8E7CB),
    tertiary = Color(0xFFA0CFCF), onTertiary = Color(0xFF003737),
    tertiaryContainer = Color(0xFF1E4E4E), onTertiaryContainer = Color(0xFFBBEBEB),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10140D), onBackground = Color(0xFFE0E4D8),
    surface = Color(0xFF10140D), onSurface = Color(0xFFE0E4D8),
    surfaceVariant = Color(0xFF43483D), onSurfaceVariant = Color(0xFFC3C8BB),
    outline = Color(0xFF8D9386), outlineVariant = Color(0xFF43483D),
    surfaceContainerLowest = Color(0xFF0B0F08), surfaceContainerLow = Color(0xFF191D16),
    surfaceContainer = Color(0xFF1D211A), surfaceContainerHigh = Color(0xFF272B24),
    surfaceContainerHighest = Color(0xFF32362E)
)

private val PinkLight = lightColorScheme(
    primary = Color(0xFF984061), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2), onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF74565E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9DE), onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF7C5636), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC1), onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F7), onBackground = Color(0xFF221920),
    surface = Color(0xFFFFF8F7), onSurface = Color(0xFF221920),
    surfaceVariant = Color(0xFFF3DDE0), onSurfaceVariant = Color(0xFF524347),
    outline = Color(0xFF847377), outlineVariant = Color(0xFFD6C2C5),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFF0F3),
    surfaceContainer = Color(0xFFFDEAF0), surfaceContainerHigh = Color(0xFFF8E4EB),
    surfaceContainerHighest = Color(0xFFF2DEE5)
)

private val PinkDark = darkColorScheme(
    primary = Color(0xFFFFB1C8), onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7B2949), onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE2BDC4), onSecondary = Color(0xFF43292E),
    secondaryContainer = Color(0xFF5B3F45), onSecondaryContainer = Color(0xFFFFD9DE),
    tertiary = Color(0xFFEEBD94), onTertiary = Color(0xFF442A10),
    tertiaryContainer = Color(0xFF5D4025), onTertiaryContainer = Color(0xFFFFDCC1),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1115), onBackground = Color(0xFFEEDEE3),
    surface = Color(0xFF1A1115), onSurface = Color(0xFFEEDEE3),
    surfaceVariant = Color(0xFF524347), onSurfaceVariant = Color(0xFFD6C2C5),
    outline = Color(0xFF9E8C90), outlineVariant = Color(0xFF524347),
    surfaceContainerLowest = Color(0xFF140B0F), surfaceContainerLow = Color(0xFF221920),
    surfaceContainer = Color(0xFF261D24), surfaceContainerHigh = Color(0xFF31282E),
    surfaceContainerHighest = Color(0xFF3C3239)
)

private val OrangeLight = lightColorScheme(
    primary = Color(0xFF8C4A00), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB7), onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF745944), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC0), onSecondaryContainer = Color(0xFF2A1707),
    tertiary = Color(0xFF5B6236), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E8B2), onTertiaryContainer = Color(0xFF181E00),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F4), onBackground = Color(0xFF221A12),
    surface = Color(0xFFFFF8F4), onSurface = Color(0xFF221A12),
    surfaceVariant = Color(0xFFF2DFD0), onSurfaceVariant = Color(0xFF514538),
    outline = Color(0xFF837467), outlineVariant = Color(0xFFD5C3B5),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFCEFE3),
    surfaceContainer = Color(0xFFF6E9DD), surfaceContainerHigh = Color(0xFFF1E4D8),
    surfaceContainerHighest = Color(0xFFEBDECF)
)

private val OrangeDark = darkColorScheme(
    primary = Color(0xFFFFB873), onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF6B3900), onPrimaryContainer = Color(0xFFFFDDB7),
    secondary = Color(0xFFE2C0A4), onSecondary = Color(0xFF422B1A),
    secondaryContainer = Color(0xFF5B412E), onSecondaryContainer = Color(0xFFFFDCC0),
    tertiary = Color(0xFFC4CC97), onTertiary = Color(0xFF2D330F),
    tertiaryContainer = Color(0xFF434A25), onTertiaryContainer = Color(0xFFE0E8B2),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF19120C), onBackground = Color(0xFFEFE0D3),
    surface = Color(0xFF19120C), onSurface = Color(0xFFEFE0D3),
    surfaceVariant = Color(0xFF514538), onSurfaceVariant = Color(0xFFD5C3B5),
    outline = Color(0xFF9D8E80), outlineVariant = Color(0xFF514538),
    surfaceContainerLowest = Color(0xFF130D08), surfaceContainerLow = Color(0xFF221A12),
    surfaceContainer = Color(0xFF261E16), surfaceContainerHigh = Color(0xFF31281F),
    surfaceContainerHighest = Color(0xFF3C3329)
)

private val TealLight = lightColorScheme(
    primary = Color(0xFF00696E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF7FE), onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6363), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E7), onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B6074), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF), onTertiaryContainer = Color(0xFF041C2F),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4FBFA), onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FBFA), onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE5E3), onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7978), outlineVariant = Color(0xFFBEC9C7),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEFF5F3),
    surfaceContainer = Color(0xFFE9EFEE), surfaceContainerHigh = Color(0xFFE3E9E8),
    surfaceContainerHighest = Color(0xFFDEE4E2)
)

private val TealDark = darkColorScheme(
    primary = Color(0xFF4CDAD8), onPrimary = Color(0xFF00373A),
    primaryContainer = Color(0xFF004F53), onPrimaryContainer = Color(0xFF6FF7FE),
    secondary = Color(0xFFB0CCCB), onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B), onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB2C8E8), onTertiary = Color(0xFF1B3250),
    tertiaryContainer = Color(0xFF324966), onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1414), onBackground = Color(0xFFDEE4E3),
    surface = Color(0xFF0E1414), onSurface = Color(0xFFDEE4E3),
    surfaceVariant = Color(0xFF3F4948), onSurfaceVariant = Color(0xFFBEC9C7),
    outline = Color(0xFF889392), outlineVariant = Color(0xFF3F4948),
    surfaceContainerLowest = Color(0xFF090F0F), surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121), surfaceContainerHigh = Color(0xFF242B2B),
    surfaceContainerHighest = Color(0xFF2F3635)
)

/**
 * Named theme packs. Each pack defines a full M3 light + dark color scheme.
 * "Dynamic" delegates to Android 12+ Material You (falls back to Purple on < 31).
 */
enum class ThemePack(
    val displayName: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    /** Manifest activity-alias suffix for the themed launcher icon. */
    private val iconAlias: String
) {
    DYNAMIC("Material You (system)", PurpleLight, PurpleDark, "LauncherPurple"),
    PURPLE("Purple (default)", PurpleLight, PurpleDark, "LauncherPurple"),
    BLUE("Ocean Blue", BlueLight, BlueDark, "LauncherBlue"),
    GREEN("Forest Green", GreenLight, GreenDark, "LauncherGreen"),
    PINK("Rose Pink", PinkLight, PinkDark, "LauncherPink"),
    ORANGE("Sunset Orange", OrangeLight, OrangeDark, "LauncherOrange"),
    TEAL("Teal Mist", TealLight, TealDark, "LauncherTeal");

    companion object {
        private const val PREFS_NAME = "baby_tracker_prefs"
        private const val KEY_THEME = "theme_pack"
        private const val DEFAULT_THEME = "DYNAMIC"
        private val ALL_ALIASES = listOf(
            "LauncherPurple", "LauncherBlue", "LauncherGreen",
            "LauncherPink", "LauncherOrange", "LauncherTeal"
        )

        fun load(context: Context): ThemePack {
            val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
            return entries.firstOrNull { it.name == name } ?: DYNAMIC
        }

        fun save(context: Context, pack: ThemePack) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, pack.name).apply()
        }

        /**
         * Enable the activity-alias for [pack]'s icon and disable all others.
         * The home-screen icon updates within a few seconds, but the OS kills
         * the app process during the component switch — call this right before
         * asking the user to restart.
         *
         * Safe to call on every launch: if the alias is already correct, the
         * PackageManager calls are no-ops (same enabled state).
         */
        fun applyIcon(context: Context, pack: ThemePack) {
            val pm = context.packageManager
            val pkg = context.packageName
            val targetAlias = pack.iconAlias

            for (alias in ALL_ALIASES) {
                val enabled = if (alias == targetAlias)
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, "$pkg.$alias"),
                    enabled,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
