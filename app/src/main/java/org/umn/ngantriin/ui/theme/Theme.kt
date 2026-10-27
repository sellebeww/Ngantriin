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
    secondary = OrangeAccent,
    onSecondary = Color.White,
    secondaryContainer = OrangeAccentContainer,
    onSecondaryContainer = Color(0xFF6B3109),
    tertiary = Saffron40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFAB4D),
    onTertiaryContainer = Color(0xFF42230D),
    background = Cloud,
    onBackground = Ink900,
    surface = SurfaceWhite,
    surfaceDim = Ink100,
    surfaceBright = SurfaceWhite,
    surfaceContainerLowest = SurfaceWhite,
    surfaceContainerLow = Cloud,
    surfaceContainer = Ink100,
    surfaceContainerHigh = Color(0xFFF0E3D5),
    surfaceContainerHighest = Ink200,
    onSurface = Ink900,
    surfaceVariant = Ink100,
    onSurfaceVariant = Ink500,
    outline = Ink200,
    outlineVariant = Ink100,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFCE0DE),
    onErrorContainer = Color(0xFF5C1512),
    inverseSurface = Color(0xFF472B16),
    inverseOnSurface = Cloud
)

private val DarkColors = darkColorScheme(
    primary = Saffron60,
    onPrimary = Color(0xFF3A1304),
    primaryContainer = Saffron40,
    onPrimaryContainer = Saffron90,
    secondary = Color(0xFFFFBD7A),
    onSecondary = Color(0xFF492000),
    secondaryContainer = Color(0xFF63300C),
    onSecondaryContainer = OrangeAccentContainer,
    tertiary = Saffron60,
    onTertiary = Ink900,
    tertiaryContainer = Color(0xFF8B3B09),
    onTertiaryContainer = Color(0xFFFFF3E7),
    background = Ink900,
    onBackground = Color(0xFFF5F0E8),
    surface = Ink800,
    surfaceDim = Ink900,
    surfaceBright = Color(0xFF564032),
    surfaceContainerLowest = Color(0xFF221912),
    surfaceContainerLow = Ink800,
    surfaceContainer = Color(0xFF3D2F24),
    surfaceContainerHigh = Color(0xFF473629),
    surfaceContainerHighest = Color(0xFF564032),
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
 * product meaning rather than a role, and they must stay stable
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
    val tableReady: Color,
    val tableReadyContainer: Color,
    val completed: Color,
    val completedContainer: Color,
    val cancelled: Color,
    val cancelledContainer: Color,
    val expired: Color,
    val expiredContainer: Color,
    val positive: Color,
    val positiveContainer: Color,
    val offline: Color
)

private val LightStatusColors = StatusColors(
    waiting = StatusWaiting, waitingContainer = StatusWaitingContainer,
    almostThere = StatusAlmostThere, almostThereContainer = StatusAlmostThereContainer,
    called = StatusCalled, calledContainer = StatusCalledContainer,
    checkedIn = StatusCheckedIn, checkedInContainer = StatusCheckedInContainer,
    tableReady = StatusTableReady, tableReadyContainer = StatusTableReadyContainer,
    completed = StatusCompleted, completedContainer = StatusCompletedContainer,
    cancelled = StatusCancelled, cancelledContainer = StatusCancelledContainer,
    expired = StatusExpired, expiredContainer = StatusExpiredContainer,
    positive = StatusPositive, positiveContainer = StatusPositiveContainer,
    offline = OfflineSlate
)

private val DarkStatusColors = StatusColors(
    waiting = Color(0xFFF2C067), waitingContainer = Color(0xFF4A3407),
    almostThere = Color(0xFFC4B1FF), almostThereContainer = Color(0xFF2E1A5E),
    called = Color(0xFFFFA97A), calledContainer = Color(0xFF55220A),
    checkedIn = Color(0xFF9CBBFF), checkedInContainer = Color(0xFF16295C),
    tableReady = Color(0xFF9BDB74), tableReadyContainer = Color(0xFF1F3A0C),
    completed = Color(0xFF6FD4BE), completedContainer = Color(0xFF07382C),
    cancelled = Color(0xFFFF9D95), cancelledContainer = Color(0xFF4F120E),
    expired = Color(0xFFC9C4BF), expiredContainer = Color(0xFF3A3633),
    positive = Color(0xFF6CD99A), positiveContainer = Color(0xFF0A3E22),
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
