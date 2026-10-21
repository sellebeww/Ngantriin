package org.umn.ngantriin.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens. Frequent things are fast; only the ticket gets a slower,
 * springier move, because it happens once per visit.
 */
object Motion {
    /** Pressed, selected, small state changes. */
    const val FAST_MS = 150

    /** Screen-level fades. Exits are always shorter than entrances. */
    const val ENTER_MS = 220
    const val EXIT_MS = 140

    /** Default for layout and value changes. */
    fun <T> standard(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.85f, stiffness = 500f)

    /** The ticket printing and the line moving forward. */
    fun <T> emphasized(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow)

    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST_MS)
}
