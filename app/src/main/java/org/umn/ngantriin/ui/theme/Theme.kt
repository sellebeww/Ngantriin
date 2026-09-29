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
    // Dark ink, not white: the brand orange doesn't clear AA contrast for
    // white text at button-label sizes (see Color.kt), dark ink does.
    onPrimary = Ink900,
    primaryContainer = Saffron90,
    onPrimaryContainer = Saffron20,
    secondary = Teal50,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Color(0xFF05392F),
    tertiary = Ink700,
    onTertiary = Color.White,
    background = Cloud,
    onBackground = Ink900,
    surface = SurfaceWhite,
    onSurface = Ink900,
    surfaceVariant = Ink100,
    onSurfaceVariant = Ink500,
    outline = Ink200,
    outlineVariant = Ink100,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFCE0DE),
    onErrorContainer = Color(0xFF5C1512),
    inverseSurface = Ink900,
    inverseOnSurface = Cloud
)

private val DarkColors = darkColorScheme(
    primary = Saffron60,
    onPrimary = Color(0xFF3A1304),
    primaryContainer = Saffron40,
    onPrimaryContainer = Saffron90,
    secondary = Color(0xFF6FD4BE),
    onSecondary = Color(0xFF00382C),
    secondaryContainer = Color(0xFF0B5C4C),
    onSecondaryContainer = Teal90,
    tertiary = Ink200,
    onTertiary = Ink900,
    background = Ink900,
    onBackground = Color(0xFFF5F0E8),
    surface = Ink800,
    onSurface = Color(0xFFF5F0E8),
    surfaceVariant = Color(0xFF3A332B),
    onSurfaceVariant = Ink300,
    outline = Color(0xFF54493D),
    outlineVariant = Color(0xFF3A332B),
    error = Color(0xFFFF897D),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Cloud,
    inverseOnSurface = Ink900
)

/**
 * Status colours are intentionally outside the Material scheme: they carry
 * product meaning (section 14) rather than a role, and they must stay stable
 * when the scheme changes.
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
    val completed: Color,
    val completedContainer: Color,
    val cancelled: Color,
    val cancelledContainer: Color,
    val offline: Color
)

private val LightStatusColors = StatusColors(
    waiting = StatusWaiting, waitingContainer = StatusWaitingContainer,
    almostThere = StatusAlmostThere, almostThereContainer = StatusAlmostThereContainer,
    called = StatusCalled, calledContainer = StatusCalledContainer,
    checkedIn = StatusCheckedIn, checkedInContainer = StatusCheckedInContainer,
    completed = StatusCompleted, completedContainer = StatusCompletedContainer,
    cancelled = StatusCancelled, cancelledContainer = StatusCancelledContainer,
    offline = OfflineSlate
)

private val DarkStatusColors = StatusColors(
    waiting = Color(0xFFF2C067), waitingContainer = Color(0xFF4A3407),
    almostThere = Color(0xFFFFA97A), almostThereContainer = Color(0xFF55220A),
    called = Color(0xFF6CD99A), calledContainer = Color(0xFF0A3E22),
    checkedIn = Color(0xFF9CBBFF), checkedInContainer = Color(0xFF16295C),
    completed = Color(0xFF6FD4BE), completedContainer = Color(0xFF07382C),
    cancelled = Color(0xFFFF9D95), cancelledContainer = Color(0xFF4F120E),
    offline = Color(0xFF9CA3AF)
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

@Composable
fun NgantriinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic colour is deliberately not used: the brand hue and the status
    // ramp are part of how the queue reads at a glance.
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NgantriinTypography,
            shapes = NgantriinShapes,
            content = content
        )
    }
}
