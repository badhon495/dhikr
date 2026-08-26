package com.dhikr.app.ui.theme

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
