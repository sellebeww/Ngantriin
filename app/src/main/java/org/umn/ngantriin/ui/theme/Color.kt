package org.umn.ngantriin.ui.theme

import androidx.compose.ui.graphics.Color

// -----------------------------------------------------------------------------
// Ngantriin palette
//
// One warm brand hue on a near-neutral ink scale. Queue state has its own ramp
// so a status never has to borrow the brand colour and lose its meaning.
//
// The brand hue is tuned from the product's Figma reference (#FF8C00 /
// #FFAA30): the raw Figma orange only clears ~2.3:1 against white, so
// Saffron50 is deepened just enough to hit >=3:1 for icons/outlines, and
// button text uses a dark ink (onPrimary) rather than white on top of it —
// white-on-vivid-orange is the mockup's own contrast bug, not something
// worth reproducing.
// -----------------------------------------------------------------------------

// Brand
val Saffron90 = Color(0xFFFFEBD3)
val Saffron60 = Color(0xFFFFAA30)
val Saffron50 = Color(0xFFD96D00)
val Saffron40 = Color(0xFFB85300)
val Saffron20 = Color(0xFF7A3B0B)

// Ink / neutrals — warm-neutral so text and dividers sit comfortably on the
// cream background instead of reading cool/purple against it.
val Ink900 = Color(0xFF1A1410)
val Ink800 = Color(0xFF2B241E)
val Ink700 = Color(0xFF463D34)
val Ink500 = Color(0xFF665F57)
val Ink300 = Color(0xFFA39A8E)
val Ink200 = Color(0xFFDDD6C9)
val Ink100 = Color(0xFFF2ECE0)
val Cloud = Color(0xFFFFFAF0)
val SurfaceWhite = Color(0xFFFFFFFF)

// Supporting accent, used for informational surfaces only
val Teal50 = Color(0xFF117A65)
val Teal90 = Color(0xFFD3F0E9)

// Queue status ramp — section 14. Each of these is always paired with an icon
// and a text label so colour is never the only signal (section 42).
val StatusWaiting = Color(0xFFB2740B)
val StatusWaitingContainer = Color(0xFFFFF0D2)
val StatusAlmostThere = Color(0xFFC2410C)
val StatusAlmostThereContainer = Color(0xFFFFE6D5)
val StatusCalled = Color(0xFF0B7A3E)
val StatusCalledContainer = Color(0xFFD4F4E0)
val StatusCheckedIn = Color(0xFF1D4ED8)
val StatusCheckedInContainer = Color(0xFFDCE7FF)
val StatusCompleted = Color(0xFF0E7C5A)
val StatusCompletedContainer = Color(0xFFD5F2E8)
val StatusCancelled = Color(0xFF9F1F17)
val StatusCancelledContainer = Color(0xFFFCE0DE)

// Feedback
val SuccessGreen = Color(0xFF0B7A3E)
val WarningAmber = Color(0xFFB2740B)
val ErrorRed = Color(0xFFB3261E)
val OfflineSlate = Color(0xFF4B5563)
