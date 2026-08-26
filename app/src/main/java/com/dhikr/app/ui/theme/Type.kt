package com.dhikr.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dhikr.app.R

val Caprasimo = FontFamily(Font(R.font.caprasimo_regular, FontWeight.Normal))
val Figtree = FontFamily(
    Font(R.font.figtree, FontWeight.Normal),
    Font(R.font.figtree, FontWeight.Medium),
    Font(R.font.figtree, FontWeight.SemiBold),
    Font(R.font.figtree, FontWeight.Bold),
)
val NotoNaskhArabic = FontFamily(Font(R.font.noto_naskh_arabic, FontWeight.Normal))
val NotoSansBengali = FontFamily(Font(R.font.noto_sans_bengali, FontWeight.Normal))

// Counter-screen-specific styles (sizes from design/README.md's typography table)
val CounterCountStyle = TextStyle(
    fontFamily = Caprasimo,
    fontSize = 84.sp,
    letterSpacing = (-0.03f).em,
)

val CounterCountLongTextStyle = TextStyle(
    fontFamily = Caprasimo,
    fontSize = 56.sp,
    letterSpacing = (-0.03f).em,
)

val ArabicLineStyle = TextStyle(
    fontFamily = NotoNaskhArabic,
    fontSize = 30.sp,
    lineHeight = 51.sp, // 1.7 line-height
)

val TransliterationStyle = TextStyle(
    fontFamily = NotoSansBengali,
    fontSize = 14.5.sp,
    lineHeight = 21.sp, // 1.45
)

val TransliterationLongTextStyle = TextStyle(
    fontFamily = NotoSansBengali,
    fontSize = 13.5.sp,
    lineHeight = 27.sp, // 2.0
)

// Default Material3 typography so any Text composable that doesn't set an
// explicit style (or one of the custom TextStyles above) still renders in
// Figtree instead of falling back to Compose's default Roboto.
val DhikrTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
