package org.umn.ngantriin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Saffron50,
    onPrimary = Color.White,
    primaryContainer = Saffron90,
    onPrimaryContainer = Saffron20,
    // One accent only: secondary and tertiary are the same saffron family so
    // no Material default hue leaks in through a component.
    secondary = Saffron40,
    onSecondary = Color.White,
    secondaryContainer = Saffron90,
    onSecondaryContainer = Saffron20,
    tertiary = Saffron40,
    onTertiary = Color.White,
    tertiaryContainer = Saffron90,
    onTertiaryContainer = Saffron20,
    background = Paper,
    onBackground = Ink900,
    surface = Paper,
    surfaceDim = Ink100,
    surfaceBright = PaperBright,
    surfaceContainerLowest = PaperBright,
    surfaceContainerLow = Paper,
    surfaceContainer = Ink100,
    surfaceContainerHigh = Color(0xFFEDE2D5),
    surfaceContainerHighest = Ink200,
    onSurface = Ink900,
    surfaceVariant = Ink100,
    onSurfaceVariant = Ink500,
    outline = Ink200,
    outlineVariant = Ink100,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedContainer,
    onErrorContainer = Color(0xFF5C1512),
    inverseSurface = Ink900,
    inverseOnSurface = Paper,
    inversePrimary = Saffron60,
    scrim = Color.Black
)

// Designed for a night market, not inverted: the ground is charred brown and
// the ticket stays paper-coloured, so it reads as the light source on screen.
private val DarkColors = darkColorScheme(
    primary = Saffron60,
    onPrimary = Color(0xFF3A1704),
    primaryContainer = Color(0xFF4A2A10),
    onPrimaryContainer = Saffron90,
    secondary = Saffron60,
    onSecondary = Color(0xFF3A1704),
    secondaryContainer = Color(0xFF4A2A10),
    onSecondaryContainer = Saffron90,
    tertiary = Saffron60,
    onTertiary = Color(0xFF3A1704),
    tertiaryContainer = Color(0xFF4A2A10),
    onTertiaryContainer = Saffron90,
    background = Night950,
    onBackground = NightInk,
    surface = Night950,
    surfaceDim = Night950,
    surfaceBright = Night700,
    surfaceContainerLowest = Color(0xFF15110E),
    surfaceContainerLow = Night900,
    surfaceContainer = Night800,
    surfaceContainerHigh = Night700,
    surfaceContainerHighest = Night600,
    onSurface = NightInk,
    surfaceVariant = Night800,
    onSurfaceVariant = Ink300,
    outline = Night600,
    outlineVariant = Night700,
    error = ErrorRedNight,
    onError = Color(0xFF690005),
    errorContainer = ErrorRedNightContainer,
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = NightInk,
    inverseOnSurface = Night950,
    inversePrimary = Saffron50,
    scrim = Color.Black
)

/**
 * Status colours are intentionally outside the Material scheme: they carry
 * product meaning rather than a role. Three tones only — quiet ink while you
 * wait, saffron when you should move, muted once it is over — plus red for a
 * cancellation or a clock running out. Every status also has its own icon
 * and label, so colour is never the only signal.
 */
data class StatusColors(
    val waiting: Color,
    val waitingContainer: Color,
    val almostThere: Color,
    val almostThereContainer: Color,
    val called: Color,
    val calledContainer: Color,
    val checkedIn: Color,
    val checkedInContainer: Color,
    val tableReady: Color,
    val tableReadyContainer: Color,
    val completed: Color,
    val completedContainer: Color,
    val cancelled: Color,
    val cancelledContainer: Color,
    val expired: Color,
    val expiredContainer: Color,
    val offline: Color
)

private val LightStatusColors = StatusColors(
    waiting = Ink500, waitingContainer = Ink100,
    almostThere = Saffron50, almostThereContainer = Saffron90,
    called = Saffron40, calledContainer = Saffron90,
    checkedIn = Ink900, checkedInContainer = Ink100,
    tableReady = Saffron40, tableReadyContainer = Saffron90,
    completed = Ink500, completedContainer = Ink100,
    cancelled = ErrorRed, cancelledContainer = ErrorRedContainer,
    expired = Ink500, expiredContainer = Ink100,
    offline = Ink500
)

private val DarkStatusColors = StatusColors(
    waiting = Ink300, waitingContainer = Night800,
    almostThere = Saffron60, almostThereContainer = Color(0xFF4A2A10),
    called = Saffron60, calledContainer = Color(0xFF4A2A10),
    checkedIn = NightInk, checkedInContainer = Night800,
    tableReady = Saffron60, tableReadyContainer = Color(0xFF4A2A10),
    completed = Ink300, completedContainer = Night800,
    cancelled = ErrorRedNight, cancelledContainer = ErrorRedNightContainer,
    expired = Ink300, expiredContainer = Night800,
    offline = Ink300
)

/**
 * The ticket is printed paper in both themes, so its colours do not follow
 * the scheme. In dark mode the paper is dimmed a little to avoid glare.
 */
data class TicketColors(
    val paper: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val rule: Color,
    val slot: Color
)

private val LightTicketColors = TicketColors(
    paper = PaperBright,
    ink = Ink900,
    inkMuted = Ink500,
    accent = Saffron50,
    rule = Ink200,
    slot = Ink900
)

private val DarkTicketColors = TicketColors(
    paper = Color(0xFFF1E7DA),
    ink = Ink900,
    inkMuted = Color(0xFF6B5E53),
    accent = Saffron50,
    rule = Color(0xFFD6C8B9),
    slot = Color(0xFF0E0B09)
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
val LocalTicketColors = staticCompositionLocalOf { LightTicketColors }

@Composable
fun NgantriinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic colour is deliberately not used: the brand hue and the status
    // ramp are part of how the queue reads at a glance.
    val colorScheme = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
        LocalTicketColors provides if (darkTheme) DarkTicketColors else LightTicketColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NgantriinTypography,
            shapes = NgantriinShapes,
            content = content
        )
    }
}
