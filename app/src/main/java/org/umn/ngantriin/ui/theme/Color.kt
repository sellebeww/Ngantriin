package org.umn.ngantriin.ui.theme

import androidx.compose.ui.graphics.Color

// "Karcis" palette: warm thermal paper, brown-black ink, one saffron accent.
// OKLCH values are the design intent (see DESIGN.md); hex is what ships.

// Brand accent — oklch(0.56 0.15 48)
val Saffron90 = Color(0xFFFFE8D2)
val Saffron60 = Color(0xFFFFB464)
val Saffron50 = Color(0xFFBB5008)
val Saffron40 = Color(0xFF923B05)
val Saffron20 = Color(0xFF5C2504)

// Ink — oklch(0.26 0.02 55) and its tints.
val Ink900 = Color(0xFF2D241E)
val Ink500 = Color(0xFF75675C)
val Ink300 = Color(0xFFB3A496)
val Ink200 = Color(0xFFE6DACE)
val Ink100 = Color(0xFFF2E9DE)

// Paper — oklch(0.985 0.01 75)
val Paper = Color(0xFFFFFAF4)
val PaperBright = Color(0xFFFFFDF9)

// Night: charred and warm, never neutral grey.
val Night950 = Color(0xFF1A1512)
val Night900 = Color(0xFF241D18)
val Night800 = Color(0xFF2E261F)
val Night700 = Color(0xFF3A3028)
val Night600 = Color(0xFF4A3E34)
val NightInk = Color(0xFFF3EBE1)

// Feedback. Red is kept for cancellation and running-out-of-time only.
val ErrorRed = Color(0xFFB3261E)
val ErrorRedContainer = Color(0xFFFCE0DE)
val ErrorRedNight = Color(0xFFFF8A7E)
val ErrorRedNightContainer = Color(0xFF4F120E)
