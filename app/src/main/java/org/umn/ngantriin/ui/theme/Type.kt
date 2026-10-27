package org.umn.ngantriin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import org.umn.ngantriin.R

// Poppins adalah font merek (lihat sumber Figma), dibundel sebagai bobot
// statis, bukan diambil lewat API downloadable fonts, supaya tampil
// sama dengan atau tanpa Play Services / jaringan.
private val Sans = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_black, FontWeight.Black)
)

/**
 * Pemotongan baris, diatur sekali untuk seluruh aplikasi, bukan per Text:
 * - judul menyeimbangkan barisnya, jadi tidak ada satu kata yatim di baris terakhir;
 * - teks isi memakai pemotong paragraf berkualitas tinggi, yang menghindari
 * satu kata pendek menggantung di bawah blok teks ("...sesuatu\nlain.").
 */
private fun TextStyle.heading() = copy(lineBreak = LineBreak.Heading)
private fun TextStyle.paragraph() = copy(lineBreak = LineBreak.Paragraph)

/**
 * Untuk teks multibaris yang rata tengah: menyeimbangkan baris supaya
 * yang terakhir tidak jadi satu kata menggantung. Teks rata kiri memakai pemotong
 * bawaan, karena tepi kanan yang tidak rata memang wajar.
 */
fun TextStyle.balanced(): TextStyle = copy(lineBreak = LineBreak.Heading)

val NgantriinTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Black,
        fontSize = 57.sp, lineHeight = 60.sp, letterSpacing = (-1.5).sp
    ).heading(),
    displayMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = (-1).sp
    ).heading(),
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp
    ).heading(),
    headlineLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp
    ).heading(),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp
    ).heading(),
    headlineSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp
    ).heading(),
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp
    ).heading(),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp
    ).heading(),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp
    ).heading(),
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp
    ).paragraph(),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ).paragraph(),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp
    ).paragraph(),
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ).paragraph(),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp
    ).paragraph(),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.6.sp
    ).paragraph()
)

/**
 * Nomor antrean adalah elemen terbesar di layar antrean aktif, jadi
 * punya gaya sendiri dan tidak meminjam displayLarge,
 * yang juga dipakai di onboarding.
 */
val QueueNumberStyle = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Black,
    fontSize = 72.sp,
    lineHeight = 76.sp,
    letterSpacing = (-2).sp
)
