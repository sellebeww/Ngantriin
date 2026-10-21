package org.umn.ngantriin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.umn.ngantriin.R

// Two voices. Bricolage Grotesque (OFL, bundled as two subset instances built
// by scripts/assets/build-fonts.sh) carries the brand: condensed for ticket
// numbers, full width for headlines. Running text is the system face, which
// is sharper at 12-14sp than a bundled font and costs nothing to ship.
private val Display = FontFamily(Font(R.font.bricolage_display, FontWeight.Bold))
private val Ticket = FontFamily(Font(R.font.bricolage_ticket, FontWeight.ExtraBold))
private val Body = FontFamily.Default

/** Tabular, lining figures: an updating number never shifts sideways. */
private const val NUMBERS = "tnum, lnum"

/**
 * Line breaking, set once for the whole app instead of per Text:
 *  - headings balance their lines, so a title never leaves one orphan word on
 *    the last line;
 *  - body copy uses the high-quality paragraph breaker, which avoids a lone
 *    short word hanging below a block of text ("...something\nelse.").
 */
private fun TextStyle.heading() = copy(lineBreak = LineBreak.Heading)
private fun TextStyle.paragraph() = copy(lineBreak = LineBreak.Paragraph)

/**
 * For multi-line text that is centred: balances the lines so the last one is
 * never a single hanging word. Left-aligned prose keeps the default breaker,
 * where a ragged right edge is expected.
 */
fun TextStyle.balanced(): TextStyle = copy(lineBreak = LineBreak.Heading)

val NgantriinTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Ticket, fontWeight = FontWeight.ExtraBold,
        fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-1).sp,
        fontFeatureSettings = NUMBERS
    ).heading(),
    displayMedium = TextStyle(
        fontFamily = Ticket, fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp,
        fontFeatureSettings = NUMBERS
    ).heading(),
    displaySmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp
    ).heading(),
    headlineLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp
    ).heading(),
    headlineMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp,
        fontFeatureSettings = NUMBERS
    ).heading(),
    headlineSmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp
    ).heading(),
    titleLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp
    ).heading(),
    titleMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp
    ).heading(),
    titleSmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp
    ).heading(),
    bodyLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp
    ).paragraph(),
    bodyMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ).paragraph(),
    bodySmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp
    ).paragraph(),
    labelLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ).paragraph(),
    labelMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp,
        fontFeatureSettings = NUMBERS
    ).paragraph(),
    // The small-caps "overline" voice: SEDANG DILAYANI, ANTREANMU.
    labelSmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp
    ).paragraph()
)

/**
 * The printed number on the ticket — the largest thing in the app. Condensed
 * so "T-108" still fits a narrow phone on one line.
 */
val QueueNumberStyle = TextStyle(
    fontFamily = Ticket,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 96.sp,
    lineHeight = 92.sp,
    letterSpacing = (-1.5).sp,
    fontFeatureSettings = NUMBERS
)

/**
 * [base] scaled with the user's font size, but no further than [maxScale].
 * For the few display numbers that are already huge: at 200% they would
 * otherwise wrap or push the actions off screen, while staying readable.
 */
@Composable
@ReadOnlyComposable
fun cappedSp(base: TextUnit, maxScale: Float = 1.3f): TextUnit {
    val density = LocalDensity.current
    val scale = density.fontScale.coerceAtMost(maxScale)
    return with(density) { (base.value * scale).dp.toSp() }
}
